package com.zone.agri.dto.response;

import com.zone.agri.entity.enums.ConversationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerAvatar;
    private ConversationStatus status;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer unreadByShop;
    private Integer unreadByCustomer;
    private Long assignedStaffId;
    private String assignedStaffName;
}
