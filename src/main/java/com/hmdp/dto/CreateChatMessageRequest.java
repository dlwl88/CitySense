package com.hmdp.dto;

import lombok.Data;

@Data
public class CreateChatMessageRequest {

    private Long conversationId;

    private String content;

    private Double x;

    private Double y;
}
