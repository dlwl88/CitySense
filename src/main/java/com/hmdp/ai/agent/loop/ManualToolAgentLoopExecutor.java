package com.hmdp.ai.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.agent.prompt.AgentLoopPromptProvider;
import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.component.AgentLoopObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManualToolAgentLoopExecutor {

    private final ChatModel chatModel;
    private final AgentLoopToolRegistry toolRegistry;
    private final AgentLoopPromptProvider promptProvider;
    private final AiAgentProperties aiAgentProperties;
    private final ObjectMapper objectMapper;

    public AgentLoopExecution execute(AgentLoopRequest request, List<ToolExecutionLog> toolLogs) {
        return execute(request, toolLogs, AgentLoopObserver.noop());
    }

    public AgentLoopExecution execute(AgentLoopRequest request, List<ToolExecutionLog> toolLogs, AgentLoopObserver observer) {
        AgentLoopExecution execution = new AgentLoopExecution();
        execution.setMaxSteps(Math.max(aiAgentProperties.getMultiAgent().getMaxSteps(), 1));

        boolean simpleRecommendationRequest = isSimpleRecommendationRequest(request);
        boolean terminateNudgeSent = false;

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptProvider.systemPrompt()));
        if (request.getHistoryMessages() != null && !request.getHistoryMessages().isEmpty()) {
            messages.addAll(request.getHistoryMessages());
        }
        messages.add(new UserMessage(promptProvider.buildUserPrompt(request)));

        int blankResponseCount = 0;
        recordTrace(execution, toolLogs, observer, 0, AgentLoopStatus.IDLE, "AgentLoop 初始化完成", List.of());
        for (int step = 1; step <= execution.getMaxSteps(); step++) {
            execution.setTotalSteps(step);
            recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.THINKING, "第 " + step + " 轮开始思考", List.of());
            ChatResponse response;
            try {
                response = chatModel.call(new Prompt(messages, buildLoopOptions()));
            } catch (Exception ex) {
                log.warn("AgentLoop thinking failed at step={}", step, ex);
                execution.setStatus(AgentLoopStatus.ERROR);
                execution.setErrorMessage("模型思考阶段失败：" + ex.getMessage());
                execution.setFinalAnswer("抱歉，当前多轮推理过程中模型调用失败，请稍后重试。"
                        + (StringUtils.hasText(execution.getErrorMessage()) ? "（" + execution.getErrorMessage() + "）" : ""));
                recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.ERROR, execution.getErrorMessage(), List.of());
                return execution;
            }

            AssistantMessage assistantMessage = response.getResult().getOutput();
            messages.add(assistantMessage);
            if (!assistantMessage.hasToolCalls()) {
                String answer = assistantMessage.getText();
                if (!StringUtils.hasText(answer)) {
                    blankResponseCount++;
                    if (step < execution.getMaxSteps()) {
                        recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.THINKING,
                                "模型返回空内容，追加约束后重试，第 " + blankResponseCount + " 次空轮次", List.of());
                        messages.add(new UserMessage(promptProvider.blankResponseRetryPrompt()));
                        continue;
                    }
                    execution.setStatus(AgentLoopStatus.ERROR);
                    execution.setErrorMessage("模型连续返回空内容，未返回可执行工具，也未产出最终答案。");
                    execution.setFinalAnswer("抱歉，当前多轮推理连续多轮没有产出可用结果，请换一种问法再试。");
                    recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.ERROR, execution.getErrorMessage(), List.of());
                    return execution;
                }
                execution.setStatus(AgentLoopStatus.FINISHED);
                execution.setFinalAnswer(answer);
                recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.FINISHED, "模型直接给出最终答案", List.of());
                return execution;
            }

            List<String> toolNames = assistantMessage.getToolCalls().stream().map(AssistantMessage.ToolCall::name).toList();
            recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.EXECUTING, "模型请求执行工具", toolNames);

            TerminateDecision terminateDecision = extractTerminateDecision(assistantMessage.getToolCalls());
            if (terminateDecision != null && assistantMessage.getToolCalls().size() == 1) {
                execution.setStatus(AgentLoopStatus.FINISHED);
                execution.setTerminatedByTool(true);
                execution.setFinalAnswer(terminateDecision.answer());
                recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.FINISHED,
                        StringUtils.hasText(terminateDecision.reason()) ? terminateDecision.reason() : "terminate 工具结束任务",
                        List.of("terminate"));
                return execution;
            }

            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                if ("terminate".equals(toolCall.name())) {
                    continue;
                }
                ToolCallback toolCallback = toolRegistry.getToolCallback(toolCall.name());
                if (toolCallback == null) {
                    execution.setStatus(AgentLoopStatus.ERROR);
                    execution.setErrorMessage("收到未注册工具调用：" + toolCall.name());
                    execution.setFinalAnswer("抱歉，当前多轮推理请求了未注册工具，任务已中止。");
                    recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.ERROR, execution.getErrorMessage(), List.of(toolCall.name()));
                    return execution;
                }
                try {
                    String responseData = toolCallback.call(toolCall.arguments(), new ToolContext(Map.of(
                            ToolContext.TOOL_CALL_HISTORY, List.copyOf(messages)
                    )));
                    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), responseData));
                    ToolExecutionLog toolLog = new ToolExecutionLog(toolCall.name(), truncatePayload(responseData));
                    toolLogs.add(toolLog);
                    notifyToolExecuted(observer, toolLog);
                } catch (Exception ex) {
                    log.warn("AgentLoop tool execution failed for tool={}", toolCall.name(), ex);
                    execution.setStatus(AgentLoopStatus.ERROR);
                    execution.setErrorMessage("工具执行失败：" + toolCall.name() + " - " + ex.getMessage());
                    execution.setFinalAnswer("抱歉，当前多轮推理在执行工具时失败，请稍后重试。"
                            + (StringUtils.hasText(execution.getErrorMessage()) ? "（" + execution.getErrorMessage() + "）" : ""));
                    recordTrace(execution, toolLogs, observer, step, AgentLoopStatus.ERROR, execution.getErrorMessage(), List.of(toolCall.name()));
                    return execution;
                }
            }

            if (!toolResponses.isEmpty()) {
                messages.add(new ToolResponseMessage(toolResponses));
                if (simpleRecommendationRequest && !terminateNudgeSent) {
                    messages.add(new UserMessage(promptProvider.simpleRecommendationFinishPrompt(request)));
                    terminateNudgeSent = true;
                }
            }
        }

        execution.setStatus(AgentLoopStatus.ERROR);
        execution.setErrorMessage("AgentLoop 超过最大步数限制：" + execution.getMaxSteps());
        execution.setFinalAnswer("抱歉，当前任务在 " + execution.getMaxSteps() + " 轮内仍未完成，已主动停止以避免无限循环。你可以补充更明确的条件后再试。");
        recordTrace(execution, toolLogs, observer, execution.getMaxSteps(), AgentLoopStatus.ERROR, execution.getErrorMessage(), List.of());
        return execution;
    }

    private ChatOptions buildLoopOptions() {
        return OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .parallelToolCalls(false)
                .toolCallbacks(toolRegistry.getToolCallbacks())
                .build();
    }

    private boolean isSimpleRecommendationRequest(AgentLoopRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            return false;
        }
        if (request.getShopId() != null) {
            return false;
        }
        String normalized = request.getPrompt()
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('？', ' ')
                .replace('！', ' ')
                .trim();

        String[] heavyToolKeywords = {
                "精确路线", "导航", "怎么去", "地铁", "打车", "步行", "离我多远", "实时天气", "下雨吗", "今天温度", "当前店铺", "这家店", "店铺ID", "商家ID"
        };
        for (String keyword : heavyToolKeywords) {
            if (normalized.contains(keyword)) {
                return false;
            }
        }

        String[] simpleKeywords = {
                "预算", "人均", "几个人", "3个人", "两个人", "三个人", "爱吃", "喜欢", "口味", "甜食", "辣", "火锅", "烧烤", "咖啡", "下午茶",
                "推荐", "想吃", "周末", "攻略", "行程", "三天", "两天", "一日游", "去哪玩", "去哪吃", "住哪", "住宿", "酒店", "游玩"
        };
        int hits = 0;
        for (String keyword : simpleKeywords) {
            if (normalized.contains(keyword)) {
                hits++;
            }
            if (hits >= 2) {
                return true;
            }
        }
        return false;
    }

    private TerminateDecision extractTerminateDecision(List<AssistantMessage.ToolCall> toolCalls) {
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (!"terminate".equals(toolCall.name())) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(toolCall.arguments());
                String answer = node.path("answer").asText("");
                String reason = node.path("reason").asText("");
                if (StringUtils.hasText(answer)) {
                    return new TerminateDecision(answer, reason);
                }
            } catch (Exception e) {
                log.warn("Failed to parse terminate tool arguments: {}", toolCall.arguments(), e);
                return null;
            }
        }
        return null;
    }

    private void recordTrace(AgentLoopExecution execution,
                             List<ToolExecutionLog> toolLogs,
                             AgentLoopObserver observer,
                             int step,
                             AgentLoopStatus status,
                             String detail,
                             List<String> toolNames) {
        execution.setStatus(status);
        AgentLoopTrace trace = new AgentLoopTrace();
        trace.setStep(step);
        trace.setStatus(status);
        trace.setDetail(detail);
        trace.getToolNames().addAll(toolNames);
        execution.getTraces().add(trace);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("status", status.name());
        payload.put("detail", detail);
        if (!toolNames.isEmpty()) {
            payload.put("toolNames", toolNames);
        }
        toolLogs.add(new ToolExecutionLog("agentLoop." + status.name().toLowerCase(), payload));
        notifyTrace(observer, trace);
    }

    private void notifyTrace(AgentLoopObserver observer, AgentLoopTrace trace) {
        try {
            observer.onTrace(trace);
        } catch (Exception ex) {
            log.warn("AgentLoop observer trace callback failed", ex);
        }
    }

    private void notifyToolExecuted(AgentLoopObserver observer, ToolExecutionLog toolLog) {
        try {
            observer.onToolExecuted(toolLog);
        } catch (Exception ex) {
            log.warn("AgentLoop observer tool callback failed", ex);
        }
    }

    private String truncatePayload(String payload) {
        if (!StringUtils.hasText(payload) || payload.length() <= 1200) {
            return payload;
        }
        return payload.substring(0, 1200) + "...";
    }

    private record TerminateDecision(String answer, String reason) {
    }
}
