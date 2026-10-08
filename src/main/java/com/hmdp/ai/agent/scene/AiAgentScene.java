package com.hmdp.ai.agent.scene;

public enum AiAgentScene {
    CHAT,
    MULTI_AGENT;

    public static AiAgentScene from(String value) {
        if (value == null || value.isBlank()) {
            return MULTI_AGENT;
        }
        for (AiAgentScene scene : values()) {
            if (scene.name().equalsIgnoreCase(value)) {
                return scene;
            }
        }
        return MULTI_AGENT;
    }
}