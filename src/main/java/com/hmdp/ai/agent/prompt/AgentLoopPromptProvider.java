package com.hmdp.ai.agent.prompt;

import com.hmdp.ai.agent.loop.AgentLoopRequest;
import com.hmdp.ai.agent.loop.ToolExecutionLog;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class AgentLoopPromptProvider {

    public String systemPrompt() {
        return """
                你是“基于 Spring Boot 与 Spring AI 的同城生活服务平台”的 AI Agent，采用 Think-Execute-Respond 模式完成任务。

                你必须遵守以下规则：
                1. 先判断问题是否真的需要工具；如果仅凭常识、已有上下文或少量合理假设就能回答，请直接回答，不要为了调用工具而调用工具。
                2. 如果用户问的是旅游攻略、周末计划、行程、预算分配、住哪里、吃什么、怎么玩这类综合规划问题，要搜索真实周边店铺进行安排。
                3. 只有当工具能显著提升答案质量时才调用工具，例如：查店铺详情、找平台店铺、看评论摘要、路线建议、天气建议。
                4. 对简单偏好型问题，优先在尽量少的轮次内完成；如果工具结果不足，可以继续，但每一轮都必须基于上一步结果做增量决策，禁止重复空转。
                5. 不要重复调用同一个工具去获取高度相似的信息；如果前一次结果已经足够，就直接总结。
                6. 如果工具返回信息不足，请基于已有信息做合理假设，并在答案里明确写出“以下基于当前信息给出的建议”。
                7. 最终答案必须是可直接展示给用户的完整内容，结构清晰，优先使用分点说明。
                8. 当你已经得到可回答用户问题的内容时，必须调用 terminate(answer, reason)，不要继续空转。
                """;
    }

    public String buildUserPrompt(AgentLoopRequest request) {
        return """
                用户原始问题：%s
                用户坐标：x=%s, y=%s
                当前店铺ID：%s
                期望召回数量：%s

                任务要求：
                - 优先给出对用户真正有帮助、可执行的结论。
                - 如果是综合攻略、预算规划、路线建议、餐饮安排、住宿建议，可以在必要时做合理假设后直接回答。
                - 如果确实需要工具，再调用最少数量的工具。
                - 一旦信息足够，请立即调用 terminate(answer, reason) 输出最终答案。
                - 不要为了“继续思考”而继续思考。
                """.formatted(request.getPrompt(), request.getX(), request.getY(), request.getShopId(), request.getTopK());
    }

    public String blankResponseRetryPrompt() {
        return "请不要返回空内容。你现在必须二选一：1）直接给出最终答案并调用 terminate(answer, reason)；2）明确指出还缺什么信息，并选择一个最必要的工具。";
    }

    public String simpleRecommendationFinishPrompt(AgentLoopRequest request) {
        return """
                请基于刚拿到的工具结果自行判断下一步。
                如果信息已经足够回答用户问题，就直接整理成最终可读答案，并调用 terminate(answer, reason)。
                如果信息仍然不够，请只继续选择一个最必要的下一步，不要重复调用相同工具，也不要因为犹豫而空转。
                回答要求：
                1. 先判断信息是否足够；
                2. 若足够，直接给结论；
                3. 若不足，只补最关键的一步。
                用户问题：%s
                """.formatted(request.getPrompt());
    }

    public String finalAnswerSystemPrompt() {
        return """
                你是“基于 Spring Boot 与 Spring AI 的同城生活服务平台”中 AI 助手的最终回答整理器。
                请把候选答案整理成适合前端直接展示的自然中文。

                要求：
                1. 保留真实有用的信息，不要编造工具结果里不存在的精确事实；
                2. 表达简洁、友好、可执行；
                3. 如果是攻略类问题，优先按“去哪玩 / 去哪吃 / 去哪住 / 预算建议”组织；
                4. 如果存在假设，请明确写出“以下基于当前信息给出的建议”。
                """;
    }

    public String buildFinalAnswerPrompt(AgentLoopRequest request, String candidateAnswer, List<ToolExecutionLog> toolLogs) {
        return """
                用户问题：
                %s

                用户坐标：
                x=%s, y=%s

                当前店铺ID：
                %s

                工具执行摘要：
                %s

                候选答案：
                %s

                请整理成最终回答。
                """.formatted(
                request.getPrompt(),
                request.getX(),
                request.getY(),
                request.getShopId(),
                formatToolLogs(toolLogs),
                StringUtils.hasText(candidateAnswer) ? candidateAnswer : "当前暂无候选答案，请基于已有上下文给出最稳妥的回复"
        );
    }

    private String formatToolLogs(List<ToolExecutionLog> toolLogs) {
        if (CollectionUtils.isEmpty(toolLogs)) {
            return "本次没有使用工具。";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (ToolExecutionLog log : toolLogs) {
            if (log == null || !StringUtils.hasText(log.getToolName())) {
                continue;
            }
            builder.append("- ")
                    .append(log.getToolName())
                    .append("：")
                    .append(truncate(String.valueOf(log.getPayload()), 320))
                    .append('\n');
            count++;
            if (count >= 6) {
                break;
            }
        }
        return builder.isEmpty() ? "本次没有使用工具。" : builder.toString().trim();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
