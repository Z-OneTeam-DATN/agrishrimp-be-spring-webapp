package com.zone.agri.security;

import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private final JwtUtils jwtUtils;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
    String token = jwtUtils.extractBearerToken(bearerToken);
    if (token != null && jwtUtils.validateToken(token)) {
      Authentication authentication = jwtUtils.setAuthentication(token);
      CustomUserDetail principal = (CustomUserDetail) authentication.getPrincipal();

      // 1. Token phat hanh truoc lan doi mat khau gan nhat (tokenVersion lech) -> coi nhu chua
      // dang nhap, buoc phai lay access token moi (se bi tu choi o /auth/refresh) roi dang nhap lai.
      // 2. principal.isEnabled() duoc CustomUserDetailsService tinh MOI o moi request tu
      // user.getStatus() hien tai trong DB (khong phai gia tri luc dang nhap) -> admin khoa/chan
      // tai khoan cung bi day ra ngay o request ke tiep, khong can doi token het han.
      if (Objects.equals(jwtUtils.extractTokenVersion(token), principal.getUserDetail().getTokenVersion())
          && principal.isEnabled()) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);

  }
}

