package com.hmdp.ai.controller;

import com.hmdp.ai.dto.AgentChatRequest;
import com.hmdp.ai.dto.AgentRecommendRequest;
import com.hmdp.ai.dto.AgentRecommendResponse;
import com.hmdp.ai.dto.ConversationSummaryDTO;
import com.hmdp.ai.dto.IndexRebuildResponse;
import com.hmdp.ai.dto.MessageDTO;
import com.hmdp.ai.dto.ShopInsightResponse;
import com.hmdp.ai.service.AiAgentService;
import com.hmdp.dto.CreateChatMessageRequest;
import com.hmdp.dto.CreateChatMessageResponse;
import com.hmdp.utils.SlidingWindowLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/agent")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    @SlidingWindowLimit(keySpEL = "'ai-create-conversation'", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 20)
    @PostMapping("/conversations")
    public ConversationSummaryDTO createConversation() {
        return aiAgentService.createConversation();
    }

    @SlidingWindowLimit(keySpEL = "#conversationId", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 20)
    @GetMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamConversation(@PathVariable("id") Long conversationId) {
        return aiAgentService.streamConversation(conversationId);
    }

    @SlidingWindowLimit(keySpEL = "'ai-message'", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 12)
    @PostMapping("/messages")
    public CreateChatMessageResponse createChatMessage(@RequestBody CreateChatMessageRequest request) {
        return aiAgentService.createChatMessage(request);
    }

    @SlidingWindowLimit(keySpEL = "'ai-chat'", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 12)
    @PostMapping("/chat")
    public CreateChatMessageResponse chat(@RequestBody AgentChatRequest request) {
        CreateChatMessageRequest createRequest = new CreateChatMessageRequest();
        createRequest.setConversationId(request.getConversationId());
        createRequest.setContent(request.getPrompt());
        createRequest.setX(request.getX());
        createRequest.setY(request.getY());
        return aiAgentService.createChatMessage(createRequest);
    }

    @SlidingWindowLimit(keySpEL = "'ai-list-conversations'", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 60)
    @GetMapping("/conversations")
    public List<ConversationSummaryDTO> conversations() {
        return aiAgentService.listConversations();
    }

    @SlidingWindowLimit(keySpEL = "#conversationId", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 60)
    @GetMapping("/conversations/{id}/messages")
    public List<MessageDTO> messages(@PathVariable("id") Long conversationId) {
        return aiAgentService.listMessages(conversationId);
    }

    @SlidingWindowLimit(keySpEL = "'ai-recommend'", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 10)
    @PostMapping("/recommend")
    public AgentRecommendResponse recommend(@RequestBody AgentRecommendRequest request) {
        return aiAgentService.recommend(request);
    }

    @SlidingWindowLimit(keySpEL = "#shopId", windowSize = 1, timeUnit = TimeUnit.MINUTES, maxRequests = 10)
    @GetMapping("/analyze/shop/{shopId}")
    public ShopInsightResponse analyzeShop(@PathVariable("shopId") Long shopId) {
        return aiAgentService.analyzeShop(shopId);
    }

    @SlidingWindowLimit(keySpEL = "'ai-index-all'", windowSize = 10, timeUnit = TimeUnit.MINUTES, maxRequests = 2)
    @PostMapping("/index/rebuild/all")
    public IndexRebuildResponse rebuildAll() {
        return aiAgentService.rebuildAll();
    }

    @SlidingWindowLimit(keySpEL = "#shopId", windowSize = 10, timeUnit = TimeUnit.MINUTES, maxRequests = 3)
    @PostMapping("/index/rebuild/shop/{shopId}")
    public IndexRebuildResponse rebuildShop(@PathVariable("shopId") Long shopId) {
        return aiAgentService.rebuildShop(shopId);
    }
}