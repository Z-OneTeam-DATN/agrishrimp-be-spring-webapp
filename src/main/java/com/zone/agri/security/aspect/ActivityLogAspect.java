package com.zone.agri.security.aspect;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.ActivityLogCatalog;
import com.zone.agri.service.ActivityLogCatalog.PermissionActivity;
import com.zone.agri.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogAspect {

    private final ActivityLogService activityLogService;

    @AfterReturning(
            pointcut = "@annotation(com.zone.agri.security.annotation.RequirePermission)"
                    + " || @within(com.zone.agri.security.annotation.RequirePermission)",
            returning = "result")
    public void writeActivityLog(JoinPoint joinPoint, Object result) {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null || !isMutationMethod(request.getMethod())) {
                return;
            }

            RequirePermission annotation = resolveAnnotation(joinPoint);
            if (annotation == null || annotation.value().length == 0) {
                return;
            }

            Optional<ResolvedPermission> resolved = resolvePermission(annotation.value(), request.getMethod());
            if (resolved.isEmpty()) {
                return;
            }

            UserDetail currentUser = AuthUtils.getUserDetail();
            if (currentUser == null) {
                return;
            }

            TargetInfo targetInfo = resolveTargetInfo(request, result);

            activityLogService.record(
                    currentUser.getId(),
                    currentUser.getFullName(),
                    currentUser.getRole() == null ? null : currentUser.getRole().getSlug(),
                    resolveBranchId(currentUser, request, joinPoint.getArgs()),
                    resolved.get().activity().module(),
                    resolved.get().activity().action(),
                    resolved.get().permissionCode(),
                    resolved.get().activity().module(),
                    targetInfo.id(),
                    targetInfo.label(),
                    request.getMethod(),
                    request.getRequestURI(),
                    clientIp(request),
                    request.getHeader("User-Agent"));
        } catch (Exception ex) {
            log.warn("Unable to write activity log", ex);
        }
    }

    private Optional<ResolvedPermission> resolvePermission(String[] permissionCodes, String httpMethod) {
        return Arrays.stream(permissionCodes)
                .map(code -> ActivityLogCatalog.fromPermissionCode(code, httpMethod)
                        .map(activity -> new ResolvedPermission(code, activity)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private RequirePermission resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }

    private boolean isMutationMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long resolveBranchId(UserDetail currentUser, HttpServletRequest request, Object[] args) {
        if (currentUser.getBranchId() != null) {
            return currentUser.getBranchId();
        }

        Long requestBranchId = parseLong(request.getParameter("branchId"));
        if (requestBranchId != null) {
            return requestBranchId;
        }

        Long warehouseId = parseLong(request.getParameter("warehouseId"));
        if (warehouseId != null) {
            return warehouseId;
        }

        for (Object arg : args) {
            Long branchId = readLongProperty(arg, "getBranchId");
            if (branchId != null) {
                return branchId;
            }
            branchId = readLongProperty(arg, "getWarehouseId");
            if (branchId != null) {
                return branchId;
            }
            branchId = readLongProperty(arg, "getFromBranchId");
            if (branchId != null) {
                return branchId;
            }
            branchId = readLongProperty(arg, "getToBranchId");
            if (branchId != null) {
                return branchId;
            }
        }

        return null;
    }

    private TargetInfo resolveTargetInfo(HttpServletRequest request, Object result) {
        Object body = result instanceof ResponseEntity<?> response ? response.getBody() : result;
        String id = stringify(readProperty(body, "getId"));
        String label = firstNonBlank(
                stringify(readProperty(body, "getCode")),
                stringify(readProperty(body, "getName")),
                stringify(readProperty(body, "getDisplayName")),
                stringify(readProperty(body, "getFullName")),
                stringify(readProperty(body, "getTitle")));

        if (id == null) {
            id = extractLastNumericPathSegment(request.getRequestURI());
        }

        return new TargetInfo(id, label);
    }

    private Object readProperty(Object source, String getterName) {
        if (source == null) {
            return null;
        }
        try {
            Method getter = source.getClass().getMethod(getterName);
            return getter.invoke(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long readLongProperty(Object source, String getterName) {
        return parseLong(stringify(readProperty(source, getterName)));
    }

    private String extractLastNumericPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("\\d+")) {
                return segments[i];
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private record ResolvedPermission(String permissionCode, PermissionActivity activity) {
    }

    private record TargetInfo(String id, String label) {
    }
}
