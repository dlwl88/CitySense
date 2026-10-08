package com.hmdp.ai.agent.loop;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentLoopRequest {

    private String prompt;

    private Double x;

    private Double y;

    private Long shopId;

    private Integer topK;

    private List<Message> historyMessages = new ArrayList<>();
}