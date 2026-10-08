package com.hmdp.ai.agent.loop;

import lombok.Data;

import java.util.List;

@Data
public class AgentLoopResult {

    private String scene;

    private AgentLoopExecution agentLoopExecution;

    private List<ToolExecutionLog> toolLogs;
}