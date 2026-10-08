package com.hmdp.ai.tool;

import com.hmdp.ai.dto.RouteAdviceDTO;
import com.hmdp.ai.dto.WeatherDiningAdviceDTO;
import com.hmdp.ai.mcp.LocalLifeMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class McpLocalLifeTools {

    private final LocalLifeMcpService localLifeMcpService;

    @Tool(description = "通过 MCP 地图/路线工具获取到店距离、交通方式和路线建议；如果 MCP 服务不可用，会自动降级为本地路线估算建议")
    public RouteAdviceDTO getRouteAdvice(
            @ToolParam(description = "目标店铺名称") String destinationName,
            @ToolParam(description = "目标店铺地址") String destinationAddress,
            @ToolParam(description = "用户经度") Double userX,
            @ToolParam(description = "用户纬度") Double userY,
            @ToolParam(description = "店铺经度") Double shopX,
            @ToolParam(description = "店铺纬度") Double shopY) {
        return localLifeMcpService.routeAdvice(destinationName, destinationAddress, userX, userY, shopX, shopY);
    }

    @Tool(description = "通过 MCP 天气工具获取天气摘要，并给出今天更适合火锅、咖啡、露天店还是室内店的建议；如果 MCP 服务不可用，会自动降级为本地季节性建议")
    public WeatherDiningAdviceDTO getWeatherDiningAdvice(
            @ToolParam(description = "城市名称，例如北京") String city,
            @ToolParam(description = "用餐场景，例如露天、火锅、咖啡、约会、聚餐") String diningScene) {
        return localLifeMcpService.weatherAdvice(city, diningScene);
    }
}