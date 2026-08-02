package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.response.activity.ActivityLogModuleResponse;
import com.zone.agri.dto.response.activity.ActivityLogResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.repository.ActivityLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public Page<ActivityLogResponse> search(
            Long actorUserId,
            Long branchId,
            String module,
            LocalDate fromDate,
            LocalDate toDate,
            String keyword,
            Pageable pageable) {
        UserDetail currentUser = requireCurrentUser();
        boolean adminLike = isCurrentUserAdminLike(currentUser);

        Long finalActorUserId = adminLike ? actorUserId : currentUser.getId();
        Long finalBranchId = currentUser.getBranchId() != null ? currentUser.getBranchId() : branchId;

        Page<ActivityLogResponse> page = activityLogRepository.search(
                finalActorUserId,
                finalBranchId,
                module,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.atTime(LocalTime.MAX),
                keyword,
                pageable);

        page.getContent().forEach(this::decorateLabels);
        return page;
    }

    public List<ActivityLogModuleResponse> getModules() {
        Set<String> modules = new LinkedHashSet<>(ActivityLogCatalog.moduleLabels().keySet());
        modules.addAll(activityLogRepository.findDistinctModules());

        return modules.stream()
                .map(module -> ActivityLogModuleResponse.builder()
                        .code(module)
                        .label(ActivityLogCatalog.moduleLabel(module))
                        .build())
                .toList();
    }

    public void record(
            Long actorUserId,
            String actorName,
            String actorRoleSlug,
            Long branchId,
            String module,
            String action,
            String permissionCode,
            String targetType,
            String targetId,
            String targetLabel,
            String httpMethod,
            String requestPath,
            String ipAddress,
            String userAgent) {
        String message = buildMessage(actorName, module, action, targetId, targetLabel);

        activityLogRepository.save(
                actorUserId,
                truncate(actorName, 150),
                truncate(actorRoleSlug, 80),
                branchId,
                module,
                action,
                truncate(permissionCode, 120),
                truncate(targetType, 120),
                truncate(targetId, 120),
                truncate(targetLabel, 255),
                truncate(message, 500),
                truncate(httpMethod, 20),
                truncate(requestPath, 255),
                truncate(ipAddress, 64),
                truncate(userAgent, 255));
    }

    private UserDetail requireCurrentUser() {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("Người dùng chưa đăng nhập.");
        }
        return currentUser;
    }

    private boolean isCurrentUserAdminLike(UserDetail currentUser) {
        return currentUser.getRole() != null
                && RoleUtils.isAdminLikeRole(currentUser.getRole().getSlug());
    }

    private void decorateLabels(ActivityLogResponse log) {
        log.setModuleLabel(ActivityLogCatalog.moduleLabel(log.getModule()));
        log.setActionLabel(ActivityLogCatalog.actionLabel(log.getAction()));
    }

    private String buildMessage(
            String actorName,
            String module,
            String action,
            String targetId,
            String targetLabel) {
        String safeActor = actorName == null || actorName.isBlank() ? "Hệ thống" : actorName;
        String target = targetLabel != null && !targetLabel.isBlank()
                ? targetLabel
                : targetId != null && !targetId.isBlank() ? "#" + targetId : "";

        String suffix = target.isBlank() ? "" : " " + target;
        return safeActor + " đã " + ActivityLogCatalog.actionLabel(action).toLowerCase()
                + " " + ActivityLogCatalog.moduleLabel(module).toLowerCase()
                + suffix;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
