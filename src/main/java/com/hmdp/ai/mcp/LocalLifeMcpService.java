package com.hmdp.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.dto.RouteAdviceDTO;
import com.hmdp.ai.dto.WeatherDiningAdviceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 轻量 MCP Client：
 * 1. 对接外部路线 / 天气工具
 * 2. 做白名单校验、超时控制
 * 3. 失败后自动降级到本地兜底策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalLifeMcpService {

    private final AiAgentProperties aiAgentProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public RouteAdviceDTO routeAdvice(String destinationName,
                                      String destinationAddress,
                                      Double userX,
                                      Double userY,
                                      Double shopX,
                                      Double shopY) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("destinationName", destinationName);
        arguments.put("destinationAddress", destinationAddress);
        arguments.put("userX", userX);
        arguments.put("userY", userY);
        arguments.put("shopX", shopX);
        arguments.put("shopY", shopY);
        return invokeWithFallback(
                "route",
                aiAgentProperties.getMcp().getRoute(),
                aiAgentProperties.getMcp().getRoute().getToolName(),
                arguments,
                RouteAdviceDTO.class,
                () -> buildRouteFallback(destinationName, destinationAddress, userX, userY, shopX, shopY)
        );
    }

    public WeatherDiningAdviceDTO weatherAdvice(String city, String diningScene) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("city", city);
        arguments.put("diningScene", diningScene);
        arguments.put("date", LocalDate.now().toString());
        return invokeWithFallback(
                "weather",
                aiAgentProperties.getMcp().getWeather(),
                aiAgentProperties.getMcp().getWeather().getToolName(),
                arguments,
                WeatherDiningAdviceDTO.class,
                () -> buildWeatherFallback(city, diningScene)
        );
    }

    private <T> T invokeWithFallback(String serverName,
                                     AiAgentProperties.McpServer server,
                                     String toolName,
                                     Map<String, Object> arguments,
                                     Class<T> resultType,
                                     Supplier<T> fallbackSupplier) {
        if (!aiAgentProperties.getMcp().isEnabled()) {
            return fallbackSupplier.get();
        }
        if (server == null || !server.isEnabled() || !StringUtils.hasText(server.getBaseUrl())) {
            return fallbackSupplier.get();
        }
        if (!aiAgentProperties.getMcp().getToolWhitelist().contains(toolName)) {
            log.warn("MCP tool is not in whitelist, server={}, tool={}", serverName, toolName);
            return fallbackSupplier.get();
        }

        try {
            JsonNode resultNode = invokeRemote(serverName, server, toolName, arguments);
            if (resultNode == null || resultNode.isNull() || resultNode.isMissingNode()) {
                return fallbackSupplier.get();
            }
            T result = objectMapper.convertValue(resultNode, resultType);
            if (result == null) {
                return fallbackSupplier.get();
            }
            return result;
        } catch (Exception e) {
            log.warn("MCP invoke failed, server={}, tool={}, fallback to local tool: {}", serverName, toolName, e.getMessage());
            return fallbackSupplier.get();
        }
    }

    private JsonNode invokeRemote(String serverName,
                                  AiAgentProperties.McpServer server,
                                  String toolName,
                                  Map<String, Object> arguments) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("tool", toolName);
        requestBody.put("arguments", arguments);

        String invokeUrl = normalizeUrl(server.getBaseUrl(), server.getInvokePath());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(invokeUrl))
                .timeout(Duration.ofMillis(Math.max(aiAgentProperties.getMcp().getTimeoutMillis(), 500)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-MCP-Server", serverName)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8));

        if (StringUtils.hasText(server.getApiKey())) {
            builder.header("Authorization", "Bearer " + server.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("MCP server response status=" + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.has("data")) {
            return root.get("data");
        }
        if (root.has("result")) {
            return root.get("result");
        }
        if (root.has("payload")) {
            return root.get("payload");
        }
        return root;
    }

    private RouteAdviceDTO buildRouteFallback(String destinationName,
                                              String destinationAddress,
                                              Double userX,
                                              Double userY,
                                              Double shopX,
                                              Double shopY) {
        RouteAdviceDTO dto = new RouteAdviceDTO();
        dto.setDestinationName(StringUtils.hasText(destinationName) ? destinationName : "目标店铺");
        dto.setSource("local-fallback");
        dto.setDegraded(true);

        Integer distanceMeters = estimateDistanceMeters(userX, userY, shopX, shopY);
        dto.setDistanceMeters(distanceMeters);
        if (distanceMeters == null) {
            dto.setTravelMode("到店建议");
            dto.setDurationSuggestion("请结合实时地图查看");
            dto.setRouteSummary("暂时无法获取精确路线，建议直接打开地图 App 搜索“" + dto.getDestinationName() + "”。");
            dto.getSuggestions().add("优先核对门店地址：" + (StringUtils.hasText(destinationAddress) ? destinationAddress : "请在详情页查看地址"));
            dto.getSuggestions().add("如果赶时间，优先打车；如果附近停车不便，优先地铁或骑行。");
            return dto;
        }

        dto.setTravelMode(distanceMeters <= 1800 ? "步行 / 骑行优先" : distanceMeters <= 6000 ? "地铁 / 骑行优先" : "打车 / 地铁优先");
        dto.setDurationSuggestion(buildDurationSuggestion(distanceMeters));
        dto.setRouteSummary("当前为本地兜底估算，距“" + dto.getDestinationName() + "”约 " + distanceMeters + " 米，可按 " + dto.getTravelMode() + " 规划行程。");
        dto.getSuggestions().add(distanceMeters <= 1800 ? "距离较近，步行或骑行通常最省时。" : "建议出发前先在地图确认实时拥堵和停车情况。");
        dto.getSuggestions().add("如果是饭点前往，尽量预留 10-20 分钟排队或找车位时间。");
        return dto;
    }

    private WeatherDiningAdviceDTO buildWeatherFallback(String city, String diningScene) {
        WeatherDiningAdviceDTO dto = new WeatherDiningAdviceDTO();
        dto.setCity(StringUtils.hasText(city) ? city : "当前城市");
        dto.setSource("local-fallback");
        dto.setDegraded(true);

        int month = LocalDate.now().getMonthValue();
        boolean warmSeason = month >= 5 && month <= 9;
        String normalizedScene = StringUtils.hasText(diningScene) ? diningScene.toLowerCase(Locale.ROOT) : "";
        dto.setWeatherSummary(warmSeason
                ? "当前按季节估算偏暖，外出用餐更适合轻食、咖啡、甜品或夜宵场景。"
                : "当前按季节估算偏凉，外出用餐更适合火锅、烧烤、热汤类场景。");

        if (normalizedScene.contains("露天") || normalizedScene.contains("户外")) {
            dto.setDiningSuggestion(warmSeason
                    ? "如果风不大，露天座位通常体验不错，但建议避开正午暴晒时段。"
                    : "户外体感可能偏冷，优先选择室内座位或带加热设施的门店。");
        } else if (normalizedScene.contains("火锅")) {
            dto.setDiningSuggestion(warmSeason
                    ? "如果今天偏热，火锅更适合晚餐或空调较好的门店。"
                    : "偏凉天气和火锅更匹配，适合聚餐。");
        } else {
            dto.setDiningSuggestion(warmSeason
                    ? "更推荐咖啡、甜品、茶饮或通风好的轻餐。"
                    : "更推荐火锅、炖菜、热饮或室内舒适型门店。");
        }

        dto.setSuitableCategories(warmSeason
                ? List.of("咖啡", "甜品", "茶饮", "轻食")
                : List.of("火锅", "烧烤", "热汤", "炖菜"));
        dto.setReminders(warmSeason
                ? List.of("注意补水", "露天就餐尽量避开正午", "夜间出行可优先选步行街或商场店")
                : List.of("优先选室内门店", "出门前确认排队情况", "夜间就餐注意保暖"));
        return dto;
    }

    private Integer estimateDistanceMeters(Double userX, Double userY, Double shopX, Double shopY) {
        if (userX == null || userY == null || shopX == null || shopY == null) {
            return null;
        }
        double earthRadius = 6371000.0;
        double lat1 = Math.toRadians(userY);
        double lat2 = Math.toRadians(shopY);
        double deltaLat = Math.toRadians(shopY - userY);
        double deltaLon = Math.toRadians(shopX - userX);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(earthRadius * c);
    }

    private String buildDurationSuggestion(int distanceMeters) {
        int walkMinutes = Math.max(3, (int) Math.ceil(distanceMeters / 80.0));
        int rideMinutes = Math.max(5, (int) Math.ceil(distanceMeters / 250.0));
        int taxiMinutes = Math.max(8, (int) Math.ceil(distanceMeters / 450.0));
        return "步行约 " + walkMinutes + " 分钟，骑行约 " + rideMinutes + " 分钟，打车约 " + taxiMinutes + " 分钟。";
    }

    private String normalizeUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!StringUtils.hasText(path)) {
            return normalizedBase;
        }
        return path.startsWith("/") ? normalizedBase + path : normalizedBase + "/" + path;
    }
}