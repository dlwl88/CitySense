package com.hmdp.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShopReviewSummaryDTO {

    private Long shopId;

    private Integer sampleCount;

    private String sampleSizeNotice;

    private List<String> reviewTexts = new ArrayList<>();

    private String combinedText;
}
