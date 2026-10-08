package com.hmdp.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RouteAdviceDTO {

    private String destinationName;

    private Integer distanceMeters;

    private String travelMode;

    private String durationSuggestion;

    private String routeSummary;

    private List<String> suggestions = new ArrayList<>();

    private boolean degraded;

    private String source;
}