package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class AgentRecommendItem {

    private Long shopId;

    private String shopName;

    private String area;

    private String address;

    private Long avgPrice;

    private Double rating;

    private Integer comments;

    private Integer sold;

    private String openHours;

    private Double distanceMeters;

    private Double score;

    private String reason;
}
