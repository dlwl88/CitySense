package com.hmdp.service.impl;

import com.hmdp.dto.ChatStreamEvent;
import com.hmdp.service.ChatSseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ChatSseServiceImpl implements ChatSseService {

    private static final long NO_TIMEOUT = 0L;

    private final ConcurrentHashMap<StreamKey, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(Long userId, Long conversationId) {
        StreamKey key = new StreamKey(userId, conversationId);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError(ex -> removeEmitter(key, emitter));
        send(userId, conversationId, ChatStreamEvent.builder()
                .type("connected")
                .conversationId(conversationId)
                .payload(Map.of("conversationId", conversationId))
                .createdTime(LocalDateTime.now())
                .build());
        return emitter;
    }

    @Override
    public void send(Long userId, Long conversationId, ChatStreamEvent event) {
        List<SseEmitter> emitterList = emitters.get(new StreamKey(userId, conversationId));
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(event));
            }
            catch (IOException e) {
                log.debug("Failed to send SSE event, conversationId={}, type={}", conversationId, event.getType(), e);
                removeEmitter(new StreamKey(userId, conversationId), emitter);
            }
        }
    }

    private void removeEmitter(StreamKey key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(key);
        if (emitterList == null) {
            return;
        }
        emitterList.remove(emitter);
        if (emitterList.isEmpty()) {
            emitters.remove(key, emitterList);
        }
    }

    private record StreamKey(Long userId, Long conversationId) {
    }
}
