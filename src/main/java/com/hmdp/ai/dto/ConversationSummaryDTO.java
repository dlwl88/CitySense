package com.hmdp.ai.dto;

import com.hmdp.ai.entity.AiConversation;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSummaryDTO {

    private Long id;

    private String title;

    private String scene;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    public static ConversationSummaryDTO from(AiConversation conversation) {
        ConversationSummaryDTO dto = new ConversationSummaryDTO();
        dto.setId(conversation.getId());
        dto.setTitle(conversation.getTitle());
        dto.setScene(conversation.getScene());
        dto.setCreatedTime(conversation.getCreateTime());
        dto.setUpdatedTime(conversation.getUpdateTime());
        return dto;
    }
}
