package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class AgentRecommendRequest {

    private String query;

    private Double x;

    private Double y;

    private Integer topK;
}
