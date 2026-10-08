package com.hmdp.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShopInsightResponse {

    private Long shopId;

    private String shopName;

    private Integer sampleSize;

    private String summary;

    private List<String> positiveTags = new ArrayList<>();

    private List<String> negativeTags = new ArrayList<>();

    private List<String> signatureDishes = new ArrayList<>();

    private String environmentSummary;

    private String serviceSummary;

    private String suitableCrowd;

    private List<String> riskTips = new ArrayList<>();

    private String sampleSizeNotice;
}
