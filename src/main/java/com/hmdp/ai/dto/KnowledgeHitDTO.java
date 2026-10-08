package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class KnowledgeHitDTO {

    private String id;

    private String sourceId;

    private String content;

    private Double score;
}
