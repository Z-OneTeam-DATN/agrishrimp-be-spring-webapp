package com.zone.agri.dto.response;

import com.zone.agri.entity.enums.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private MessageType messageType;
    private PinnedProductInfo pinnedProduct;
    private String imageUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class PinnedProductInfo {
        private Long id;
        private String name;
        private String slug;
        private String imageUrl;
        private String thumbnailUrl;
    }
}
