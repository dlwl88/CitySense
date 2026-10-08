package com.hmdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class AgentChatRequest {

    private Long conversationId;

    @JsonAlias("content")
    private String prompt;

    private Double x;

    private Double y;
}
