package com.hmdp.ai.agent.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.agent.prompt.AgentLoopPromptProvider;
import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.tool.AgentLoopControlTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualToolAgentLoopExecutorTest {

    @Test
    void shouldRetryWhenModelReturnsBlankResponse() {
        AtomicInteger callCount = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (callCount.incrementAndGet() == 1) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-2", "function", "terminate", "{\"answer\":\"重试后成功\",\"reason\":\"补充约束后模型正常结束\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(5);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("帮我处理一个问题");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.FINISHED, execution.getStatus());
        assertEquals("重试后成功", execution.getFinalAnswer());
        assertEquals(2, execution.getTotalSteps());
    }

    @Test
    void shouldFinishViaTerminateToolWithinLoop() {
        AtomicInteger callCount = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (callCount.incrementAndGet() == 1) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(
                            "",
                            java.util.Map.of(),
                            List.of(new AssistantMessage.ToolCall("call-1", "function", "echo", "{\"text\":\"hello\"}"))
                    ))));
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-2", "function", "terminate", "{\"answer\":\"最终答案\",\"reason\":\"信息已经足够\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(20);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("帮我一步步处理");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.FINISHED, execution.getStatus());
        assertTrue(execution.isTerminatedByTool());
        assertEquals("最终答案", execution.getFinalAnswer());
        assertEquals(2, execution.getTotalSteps());
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.THINKING));
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.EXECUTING));
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.FINISHED));
    }

    @Test
    void shouldStopWhenExceedingMaxSteps() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-loop", "function", "echo", "{\"text\":\"loop\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(3);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("一直循环");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.ERROR, execution.getStatus());
        assertFalse(execution.isTerminatedByTool());
        assertTrue(execution.getFinalAnswer().contains("3 轮内仍未完成"));
        assertEquals(3, execution.getTotalSteps());
    }

    static class TestLoopTools {

        @Tool(description = "回显输入文本")
        public String echo(@ToolParam(description = "输入文本") String text) {
            return "echo:" + text;
        }
    }
}