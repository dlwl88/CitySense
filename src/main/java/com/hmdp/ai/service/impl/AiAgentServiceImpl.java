package com.hmdp.ai.service.impl;

import com.hmdp.ai.agent.loop.AgentLoopExecution;
import com.hmdp.ai.agent.loop.AgentLoopExecutor;
import com.hmdp.ai.agent.loop.AgentLoopRequest;
import com.hmdp.ai.agent.loop.AgentLoopResult;
import com.hmdp.ai.agent.loop.AgentLoopStatus;
import com.hmdp.ai.agent.scene.AiAgentScene;
import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.dto.AgentRecommendRequest;
import com.hmdp.ai.dto.AgentRecommendResponse;
import com.hmdp.ai.dto.ConversationSummaryDTO;
import com.hmdp.ai.dto.IndexRebuildResponse;
import com.hmdp.ai.dto.MessageDTO;
import com.hmdp.ai.dto.ShopInsightResponse;
import com.hmdp.ai.memory.ConversationMemoryService;
import com.hmdp.ai.rag.indexer.AiVectorIndexService;
import com.hmdp.ai.service.AiAgentService;
import com.hmdp.dto.CreateChatMessageRequest;
import com.hmdp.dto.CreateChatMessageResponse;
import com.hmdp.dto.UserDTO;
import com.hmdp.event.ChatMessageCreatedEvent;
import com.hmdp.service.ChatSseService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    private final ConversationMemoryService conversationMemoryService;

    private final AgentLoopExecutor agentLoopExecutor;

    private final AiVectorIndexService aiVectorIndexService;

    private final AiAgentProperties aiAgentProperties;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ChatSseService chatSseService;

    @Override
    @Transactional
    public ConversationSummaryDTO createConversation() {
        var conversation = conversationMemoryService.getOrCreateConversation(
                currentUserId(),
                null,
                null,
                AiAgentScene.MULTI_AGENT.name()
        );
        return ConversationSummaryDTO.from(conversation);
    }

    @Override
    @Transactional
    public CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request) {
        Long userId = currentUserId();
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("content 不能为空");
        }

        var conversation = conversationMemoryService.getOrCreateConversation(
                userId,
                request.getConversationId(),
                request.getContent(),
                AiAgentScene.MULTI_AGENT.name()
        );
        var chatMessage = conversationMemoryService.appendUserMessage(conversation.getId(), request.getContent());
        applicationEventPublisher.publishEvent(new ChatMessageCreatedEvent(
                userId,
                conversation.getId(),
                chatMessage.getId(),
                chatMessage.getContent(),
                request.getX(),
                request.getY()
        ));
        return CreateChatMessageResponse.builder()
                .conversationId(conversation.getId())
                .chatMessageId(chatMessage.getId())
                .build();
    }

    @Override
    public SseEmitter streamConversation(Long conversationId) {
        Long userId = currentUserId();
        conversationMemoryService.ensureConversationOwnership(userId, conversationId);
        return chatSseService.connect(userId, conversationId);
    }

    @Override
    public List<ConversationSummaryDTO> listConversations() {
        return conversationMemoryService.listConversations(currentUserId());
    }

    @Override
    public List<MessageDTO> listMessages(Long conversationId) {
        return conversationMemoryService.listMessages(currentUserId(), conversationId);
    }

    @Override
    public AgentRecommendResponse recommend(AgentRecommendRequest request) {
        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt(request.getQuery());
        loopRequest.setX(request.getX());
        loopRequest.setY(request.getY());
        loopRequest.setTopK(request.getTopK());
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);

        AgentRecommendResponse response = new AgentRecommendResponse();
        response.setScene(AiAgentScene.MULTI_AGENT.name());
        response.setQuery(request.getQuery());
        response.setSummary(buildAgentLoopReply(loopResult.getAgentLoopExecution()));
        if (request.getX() == null || request.getY() == null) {
            response.setMissingInfoNotice("未提供位置，本次推荐未纳入距离因素。");
        }
        return response;
    }

    @Override
    public ShopInsightResponse analyzeShop(Long shopId) {
        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt("请分析店铺ID=" + shopId + " 的真实评价，重点关注招牌菜、环境和服务");
        loopRequest.setShopId(shopId);
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);

        ShopInsightResponse response = new ShopInsightResponse();
        response.setShopId(shopId);
        response.setSummary(buildAgentLoopReply(loopResult.getAgentLoopExecution()));
        response.setSampleSize(0);
        response.setSampleSizeNotice("当前结果由 ReAct Agent 生成，未走固定样本分析链路。");
        return response;
    }

    @Override
    public IndexRebuildResponse rebuildAll() {
        return aiVectorIndexService.rebuildAll();
    }

    @Override
    public IndexRebuildResponse rebuildShop(Long shopId) {
        return aiVectorIndexService.rebuildShop(shopId);
    }

    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("当前未登录");
        }
        return user.getId();
    }

    private String buildAgentLoopReply(AgentLoopExecution execution) {
        if (execution == null) {
            return "抱歉，当前多轮推理没有产生可用结果，请稍后重试。";
        }
        if (StringUtils.hasText(execution.getFinalAnswer())) {
            return execution.getFinalAnswer();
        }
        if (execution.getStatus() == AgentLoopStatus.ERROR && StringUtils.hasText(execution.getErrorMessage())) {
            return "抱歉，多轮推理过程中出现错误：" + execution.getErrorMessage();
        }
        return "抱歉，当前多轮推理没有产生可用结果，请稍后重试。";
    }
}
