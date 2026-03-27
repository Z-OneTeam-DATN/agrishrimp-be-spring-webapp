package com.zone.agri.config;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.user.UserDetail;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

public class AuditorAwareImpl implements AuditorAware<Long> {

  @Override
  public Optional<Long> getCurrentAuditor() {
    UserDetail userDetail = AuthUtils.getUserDetail();
    if (userDetail == null) {
      return Optional.of(Long.valueOf("9999")); // fallback
    }
    return Optional.of(userDetail.getId());
  }
}
