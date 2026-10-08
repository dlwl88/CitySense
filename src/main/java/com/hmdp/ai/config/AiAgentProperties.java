package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {

    private Memory memory = new Memory();

    private Rag rag = new Rag();

    private Model model = new Model();

    private Insight insight = new Insight();

    private MultiAgent multiAgent = new MultiAgent();

    private Bootstrap bootstrap = new Bootstrap();

    private Streaming streaming = new Streaming();

    private Mcp mcp = new Mcp();

    @Data
    public static class Memory {
        private int windowSize = 8;
    }

    @Data
    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.65;
        private double knowledgeSimilarityThreshold = 0.35;
        private Collections collections = new Collections();
    }

    @Data
    public static class Model {
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 90;
        private int writeTimeoutSeconds = 90;
        private int callTimeoutSeconds = 120;
    }

    @Data
    public static class Insight {
        private int maxReviewSamples = 8;
        private int maxReviewCharsPerSample = 280;
        private int maxCombinedChars = 2200;
        private int maxVectorHits = 4;
        private int maxVectorHitChars = 180;
    }

    @Data
    public static class MultiAgent {
        private int maxSteps = 20;
    }

    @Data
    public static class Bootstrap {
        private boolean enabled = true;
        private boolean failFast = false;
    }

    @Data
    public static class Streaming {
        private boolean enabled = true;
        private int timeoutSeconds = 45;
        private int fallbackChunkSize = 24;
    }

    @Data
    public static class Mcp {
        private boolean enabled = false;
        private int timeoutMillis = 2500;
        private List<String> toolWhitelist = new ArrayList<>(List.of("route_plan", "weather_brief"));
        private McpServer route = defaultServer("route_plan");
        private McpServer weather = defaultServer("weather_brief");
    }

    @Data
    public static class McpServer {
        private boolean enabled = false;
        private String baseUrl;
        private String invokePath = "/tools/invoke";
        private String apiKey;
        private String toolName;
    }

    @Data
    public static class Collections {
        private String knowledge = "knowledge_vector";
        private String shopProfile = "shop_profile_vector";
        private String blogReview = "blog_review_vector";
    }

    private static McpServer defaultServer(String toolName) {
        McpServer server = new McpServer();
        server.setToolName(toolName);
        return server;
    }
}