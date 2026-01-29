package com.zone.agri.controller;

import com.zone.agri.dto.auth.AuthResponse;
import com.zone.agri.dto.auth.LoginRequest;
import com.zone.agri.dto.auth.TokenRefreshRequest;
import com.zone.agri.dto.common.MessageResponse;
import com.zone.agri.dto.user.UserInDto;
import com.zone.agri.dto.user.UserOutDto;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.service.UserService;
import com.zone.agri.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class    AuthController {

  private final UserService userService;
  private final JwtUtils jwtUtils;
  private final CustomUserDetailsService userDetailsService;

  @GetMapping("/me")
  public UserOutDto me() {
    return userService.getMe();
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest inDto) {
    AuthResponse authResponse = userService.login(inDto);
    return ResponseEntity.ok(authResponse);
  }

  @PostMapping("/signup")
  public ResponseEntity<UserOutDto> signup(@RequestBody UserInDto inDto) {
    return ResponseEntity.ok(userService.signup(inDto));
  }

  @PostMapping("/logout")
  public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
    String token = request.getHeader("Authorization").replace("Bearer ", "");
    jwtUtils.revokeToken(token);
    return ResponseEntity.ok(new MessageResponse("Logout successful"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody TokenRefreshRequest request) {
    String refreshToken = request.getRefreshToken();

    if (!jwtUtils.validateToken(refreshToken)) {
      throw new CustomAuthenticationException("Invalid refresh token");
    }

    String username = jwtUtils.extractUsername(refreshToken);
    CustomUserDetail userDetails = userDetailsService.loadUserByUsername(username);

    String newAccessToken = jwtUtils.generateAccessToken(userDetails);

    return ResponseEntity.ok(new AuthResponse(newAccessToken, refreshToken));
  }
}
