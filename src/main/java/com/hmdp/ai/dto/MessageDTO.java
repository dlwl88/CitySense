package com.hmdp.ai.dto;

import com.hmdp.ai.entity.AiMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {

    private Long id;

    private String role;

    private String content;

    private String toolName;

    private String toolPayload;

    private LocalDateTime createdTime;

    public static MessageDTO from(AiMessage message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setToolName(message.getToolName());
        dto.setToolPayload(message.getToolPayload());
        dto.setCreatedTime(message.getCreateTime());
        return dto;
    }
}
