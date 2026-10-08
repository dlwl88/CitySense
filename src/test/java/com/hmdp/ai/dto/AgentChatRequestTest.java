package com.hmdp.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentChatRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializePromptField() throws Exception {
        AgentChatRequest request = objectMapper.readValue("""
                {
                  "conversationId": 1,
                  "prompt": "你好"
                }
                """, AgentChatRequest.class);

        assertEquals(1L, request.getConversationId());
        assertEquals("你好", request.getPrompt());
    }

    @Test
    void shouldDeserializeLegacyContentField() throws Exception {
        AgentChatRequest request = objectMapper.readValue("""
                {
                  "conversationId": 1,
                  "content": "你好"
                }
                """, AgentChatRequest.class);

        assertEquals(1L, request.getConversationId());
        assertEquals("你好", request.getPrompt());
    }
}