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

    if (currentUser.getBranchId() != null) {
      return currentUser.getBranchId();
    }

    return requestedBranchId;
  }
}
