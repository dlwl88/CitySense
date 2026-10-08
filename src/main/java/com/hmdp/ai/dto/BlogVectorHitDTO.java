package com.hmdp.ai.dto;

import lombok.Data;

@Data
public class BlogVectorHitDTO {

    private Long blogId;

    private Long shopId;

    private Long userId;

    private String title;

    private String content;

    private Double score;
}
