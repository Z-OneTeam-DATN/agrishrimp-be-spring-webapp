package com.zone.agri.common;

import com.zone.agri.dto.user.UserDetail;
import com.zone.agri.security.CustomUserDetail;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
