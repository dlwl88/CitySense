package com.hmdp.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WeatherDiningAdviceDTO {

    private String city;

    private String weatherSummary;

    private String diningSuggestion;

    private List<String> suitableCategories = new ArrayList<>();

    private List<String> reminders = new ArrayList<>();

    private boolean degraded;

    private String source;
}