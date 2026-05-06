package com.zone.agri.dto.response;

import com.zone.agri.entity.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String content;
    private NotificationType notificationType;
    private Boolean isRead;
    private Long referenceId;
    private LocalDateTime createdAt;
}
