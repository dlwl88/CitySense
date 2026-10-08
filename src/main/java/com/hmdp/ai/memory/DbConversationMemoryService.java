package com.hmdp.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.dto.ConversationSummaryDTO;
import com.hmdp.ai.dto.MessageDTO;
import com.hmdp.ai.entity.AiConversation;
import com.hmdp.ai.entity.AiMessage;
import com.hmdp.ai.service.AiConversationService;
import com.hmdp.ai.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbConversationMemoryService implements ConversationMemoryService {

    private static final int ACTIVE_STATUS = 1;

    private final AiConversationService conversationService;

    private final AiMessageService messageService;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AiConversation getOrCreateConversation(Long userId, Long conversationId, String prompt, String scene) {
        if (conversationId != null) {
            AiConversation existing = conversationService.lambdaQuery()
                    .eq(AiConversation::getId, conversationId)
                    .eq(AiConversation::getStatus, ACTIVE_STATUS)
                    .one();
            if (existing == null) {
                conversationId = null;
            }
            else if (!userId.equals(existing.getUserId())) {
                throw new IllegalArgumentException("会话无权限访问");
            }
            else {
                updateConversation(existing.getId(), scene, prompt);
                return existing;
            }
        }

        AiConversation conversation = new AiConversation()
                .setUserId(userId)
                .setTitle(buildTitle(prompt))
                .setScene(scene)
                .setStatus(ACTIVE_STATUS)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        conversationService.save(conversation);
        return conversation;
    }

    @Override
    @Transactional
    public void updateConversation(Long conversationId, String scene, String latestPrompt) {
        AiConversation update = new AiConversation()
                .setId(conversationId)
                .setScene(scene)
                .setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(latestPrompt)) {
            update.setTitle(buildTitle(latestPrompt));
        }
        conversationService.updateById(update);
    }

    @Override
    @Transactional
    public AiMessage appendUserMessage(Long conversationId, String content) {
        return saveMessage(conversationId, "user", content, null, null);
    }

    @Override
    @Transactional
    public AiMessage appendAssistantMessage(Long conversationId, String content) {
        return saveMessage(conversationId, "assistant", content, null, null);
    }

    @Override
    @Transactional
    public AiMessage appendToolMessage(Long conversationId, String toolName, Object payload) {
        String summary = summarizePayload(payload);
        return saveMessage(conversationId, "tool", summary, toolName, summary);
    }

    @Override
    public List<Message> loadRecentMessages(Long conversationId, int windowSize) {
        return loadRecentMessagesBefore(conversationId, null, windowSize);
    }

    @Override
    public List<Message> loadRecentMessagesBefore(Long conversationId, Long beforeMessageId, int windowSize) {
        int messageLimit = Math.max(windowSize * 2, 1);
        var query = messageService.lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, List.of("user", "assistant"));
        if (beforeMessageId != null) {
            query.lt(AiMessage::getId, beforeMessageId);
        }
        List<AiMessage> messages = query
                .orderByDesc(AiMessage::getId)
                .last("limit " + messageLimit)
                .list();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Collections.reverse(messages);
        List<Message> promptMessages = new ArrayList<>(messages.size());
        for (AiMessage message : messages) {
            if ("assistant".equals(message.getRole())) {
                promptMessages.add(new AssistantMessage(message.getContent()));
            }
            else {
                promptMessages.add(new UserMessage(message.getContent()));
            }
        }
        return promptMessages;
    }

    @Override
    public void ensureConversationOwnership(Long userId, Long conversationId) {
        boolean exists = conversationService.lambdaQuery()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getStatus, ACTIVE_STATUS)
                .exists();
        if (!exists) {
            throw new IllegalArgumentException("会话不存在或无权限访问");
        }
    }

    @Override
    public List<ConversationSummaryDTO> listConversations(Long userId) {
        return conversationService.lambdaQuery()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getStatus, ACTIVE_STATUS)
                .orderByDesc(AiConversation::getUpdateTime)
                .list()
                .stream()
                .map(ConversationSummaryDTO::from)
                .toList();
    }

    @Override
    public List<MessageDTO> listMessages(Long userId, Long conversationId) {
        ensureConversationOwnership(userId, conversationId);
        return messageService.lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId)
                .list()
                .stream()
                .map(MessageDTO::from)
                .toList();
    }

    private AiMessage saveMessage(Long conversationId, String role, String content, String toolName, String toolPayload) {
        AiMessage message = new AiMessage()
                .setConversationId(conversationId)
                .setRole(role)
                .setContent(content)
                .setToolName(toolName)
                .setToolPayload(toolPayload)
                .setCreateTime(LocalDateTime.now());
        messageService.save(message);
        return message;
    }

    private String summarizePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > 1000 ? json.substring(0, 1000) : json;
        }
        catch (JsonProcessingException e) {
            String text = String.valueOf(payload);
            return text.length() > 1000 ? text.substring(0, 1000) : text;
        }
    }

    private String buildTitle(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "新会话";
        }
        String normalized = prompt.trim().replaceAll("\\s+", " ");
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }
}
