package com.zone.agri.common;

import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.security.CustomUserDetail;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

public class AuthUtils {

  public static UserDetail getUserDetail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    if (!(authentication.getPrincipal() instanceof CustomUserDetail customUserDetail)) {
      return null;
    }
    return customUserDetail.getUserDetail();
  }

  public static Set<String> getAuthorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return Set.of();
    }

    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  public static boolean hasAuthority(String authority) {
    return getAuthorities().contains(authority);
  }

  public static boolean hasAnyAuthority(String... authorities) {
    if (authorities == null || authorities.length == 0) {
      return false;
    }

    Set<String> currentAuthorities = getAuthorities();
    return Arrays.stream(authorities).anyMatch(currentAuthorities::contains);
  }

  public static Long resolveRequestedOrUserBranch(Long requestedBranchId, String... requiredAuthorities) {
    UserDetail currentUser = getUserDetail();
    if (currentUser == null) {
      throw new AccessDeniedException("Người dùng chưa đăng nhập.");
    }

    if (requiredAuthorities != null
        && requiredAuthorities.length > 0
        && !hasAnyAuthority(requiredAuthorities)) {
      throw new AccessDeniedException("Người dùng không có quyền truy cập.");
    }

    // Super Admin luôn toàn quyền xem mọi chi nhánh — kể cả khi tài khoản của họ vô tình có sẵn
    // branchId trong DB (ví dụ dữ liệu cũ/gán nhầm). Không được ép về 1 chi nhánh cụ thể như các
    // role khác, nếu không "Tất cả chi nhánh" ở frontend sẽ không thực sự lấy dữ liệu toàn hệ thống.
    boolean isSuperAdmin = currentUser.getRole() != null
        && "SUPER_ADMIN".equalsIgnoreCase(currentUser.getRole().getSlug());
    if (isSuperAdmin) {
      return requestedBranchId;
    }

    if (currentUser.getBranchId() != null) {
      return currentUser.getBranchId();
    }

    return requestedBranchId;
  }

  /**
   * Giống {@link #resolveRequestedOrUserBranch(Long, String...)} nhưng cho phép cấp riêng 1
   * quyền "xem mọi chi nhánh" (ví dụ REPORT_FINANCE_VIEW_ALL_BRANCHES) — nhân viên không phải
   * Super Admin nhưng được cấp quyền này thì không bị khoá về chi nhánh của chính họ.
   */
  public static Long resolveRequestedOrUserBranch(
      Long requestedBranchId, String requiredAuthority, String branchOverrideAuthority) {
    UserDetail currentUser = getUserDetail();
    if (currentUser == null) {
      throw new AccessDeniedException("Người dùng chưa đăng nhập.");
    }

    if (requiredAuthority != null && !hasAuthority(requiredAuthority)) {
      throw new AccessDeniedException("Người dùng không có quyền truy cập.");
    }

    boolean isSuperAdmin = currentUser.getRole() != null
        && "SUPER_ADMIN".equalsIgnoreCase(currentUser.getRole().getSlug());
    if (isSuperAdmin) {
      return requestedBranchId;
    }

    if (branchOverrideAuthority != null && hasAuthority(branchOverrideAuthority)) {
      return requestedBranchId;
    }

    if (currentUser.getBranchId() != null) {
      return currentUser.getBranchId();
    }

    return requestedBranchId;
  }
}
