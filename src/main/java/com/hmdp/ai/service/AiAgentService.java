package com.hmdp.ai.service;

import com.hmdp.ai.dto.AgentRecommendRequest;
import com.hmdp.ai.dto.AgentRecommendResponse;
import com.hmdp.ai.dto.ConversationSummaryDTO;
import com.hmdp.ai.dto.IndexRebuildResponse;
import com.hmdp.ai.dto.MessageDTO;
import com.hmdp.ai.dto.ShopInsightResponse;
import com.hmdp.dto.CreateChatMessageRequest;
import com.hmdp.dto.CreateChatMessageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiAgentService {

    ConversationSummaryDTO createConversation();

    CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request);

    SseEmitter streamConversation(Long conversationId);

    List<ConversationSummaryDTO> listConversations();

    List<MessageDTO> listMessages(Long conversationId);

    AgentRecommendResponse recommend(AgentRecommendRequest request);

    ShopInsightResponse analyzeShop(Long shopId);

    IndexRebuildResponse rebuildAll();

    IndexRebuildResponse rebuildShop(Long shopId);
}
