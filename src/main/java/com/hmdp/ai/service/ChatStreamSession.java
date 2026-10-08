package com.hmdp.ai.service;

import reactor.core.publisher.Flux;

public class ChatStreamSession {

    private final Long conversationId;

    private final Flux<String> stream;

    public ChatStreamSession(Long conversationId, Flux<String> stream) {
        this.conversationId = conversationId;
        this.stream = stream;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Flux<String> getStream() {
        return stream;
    }
}
