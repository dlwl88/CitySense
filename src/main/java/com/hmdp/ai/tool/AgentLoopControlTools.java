package com.hmdp.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentLoopControlTools {

    @Tool(description = "结束当前任务。当你已经有足够信息可以直接回复用户时，调用此工具给出最终答案和结束原因")
    public TerminateResult terminate(
            @ToolParam(description = "给用户的最终回复，必须是可直接发送的完整答案") String answer,
            @ToolParam(description = "你决定结束任务的简短原因") String reason) {
        return new TerminateResult(answer, reason);
    }

    public record TerminateResult(String answer, String reason) {
    }
}