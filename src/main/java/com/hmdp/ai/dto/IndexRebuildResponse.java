package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class IndexRebuildResponse {

    private boolean success;

    private String scope;

    private int knowledgeCount;

    private int shopProfileCount;

    private int blogReviewCount;

    private String message;
}
