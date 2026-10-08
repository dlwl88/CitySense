package com.hmdp.ai.agent.loop;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentLoopTrace {

    private int step;

    private AgentLoopStatus status;

    private String detail;

    private List<String> toolNames = new ArrayList<>();
}