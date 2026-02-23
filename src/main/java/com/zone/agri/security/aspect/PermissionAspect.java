package com.zone.agri.security.aspect;

import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.security.annotation.RequirePermission;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP Aspect thực thi kiểm tra quyền cho annotation {@link RequirePermission}.
 *
 * <p>Aspect này chặn tất cả các method được đánh dấu với {@code @RequirePermission}
 * (ở cấp method hoặc class) và kiểm tra xem người dùng hiện tại có ít nhất một
 * trong các quyền được chỉ định hay không.</p>
 *
 * <p>Ưu tiên: annotation ở cấp METHOD ghi đè annotation ở cấp CLASS.</p>
 */
@Aspect
@Component
@Slf4j
public class PermissionAspect {

    @Before("@annotation(com.zone.agri.security.annotation.RequirePermission)" +
            " || @within(com.zone.agri.security.annotation.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {

        // 1. Lấy authentication hiện tại
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để thực hiện thao tác này");
        }

        // 2. Lấy annotation — ưu tiên method-level trước, rồi class-level
        RequirePermission annotation = resolveAnnotation(joinPoint);
        if (annotation == null) return;

        String[] requiredCodes = annotation.value();
        if (requiredCodes.length == 0) return;

        // 3. Lấy tập quyền thực tế của user
        Set<String> userAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // ADMIN bypass: ROLE_ADMIN được qua mọi permission check
        if (userAuthorities.contains("ROLE_ADMIN")) return;

        // 4. Kiểm tra OR logic — có ít nhất một permission là đủ
        boolean hasPermission = Arrays.stream(requiredCodes)
                .anyMatch(userAuthorities::contains);

        if (!hasPermission) {
            log.warn("Từ chối truy cập: user không có permission [{}]", Arrays.toString(requiredCodes));
            throw new Forbidden("Bạn không có quyền thực hiện thao tác này");
        }
    }

    /**
     * Ưu tiên annotation ở method-level, fallback sang class-level.
     */
    private RequirePermission resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) return methodAnnotation;

        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
