package com.hmdp.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentRecommendResponse {

    private String scene = "MULTI_AGENT";

    private String query;

    private String summary;

    private String missingInfoNotice;

    private List<AgentRecommendItem> items = new ArrayList<>();
}
