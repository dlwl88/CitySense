package com.hmdp.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageCreatedEvent {

    private final Long userId;

    private final Long conversationId;

    private final Long chatMessageId;

    private final String content;

    private final Double x;

    private final Double y;
}
