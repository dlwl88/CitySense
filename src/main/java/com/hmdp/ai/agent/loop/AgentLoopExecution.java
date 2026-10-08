package com.hmdp.ai.agent.loop;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentLoopExecution {

    private AgentLoopStatus status = AgentLoopStatus.IDLE;

    private int maxSteps;

    private int totalSteps;

    private boolean terminatedByTool;

    private String finalAnswer;

    private String errorMessage;

    private List<AgentLoopTrace> traces = new ArrayList<>();
}