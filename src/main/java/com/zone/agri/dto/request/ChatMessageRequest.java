package com.zone.agri.dto.request;

import com.zone.agri.entity.enums.MessageType;
import lombok.Data;

@Data
public class ChatMessageRequest {
    private Long conversationId;
    private String content;
    private MessageType messageType = MessageType.TEXT;
    private Long pinnedProductId;
}
