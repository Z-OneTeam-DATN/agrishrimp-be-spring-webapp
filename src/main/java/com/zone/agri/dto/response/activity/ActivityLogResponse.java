package com.zone.agri.dto.response.activity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponse {

    private Long id;
    private Long actorUserId;
    private String actorName;
    private String actorRoleSlug;
    private Long branchId;
    private String branchName;
    private String module;
    private String moduleLabel;
    private String action;
    private String actionLabel;
    private String permissionCode;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String message;
    private String httpMethod;
    private String requestPath;
    private String ipAddress;
    private LocalDateTime createdAt;
}
