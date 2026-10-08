package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class ShopToolDTO {

    private Long shopId;

    private String name;

    private Long typeId;

    private String area;

    private String address;

    private Long avgPrice;

    private Double rating;

    private Integer comments;

    private Integer sold;

    private String openHours;

    private Double x;

    private Double y;

    private Double distanceMeters;

    private Double semanticScore;
}
