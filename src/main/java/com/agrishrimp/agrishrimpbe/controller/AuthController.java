package com.agrishrimp.agrishrimpbe.controller;

import com.agrishrimp.agrishrimpbe.dto.auth.*;
import com.agrishrimp.agrishrimpbe.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API xác thực người dùng")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Đăng nhập bằng Email/SĐT và Mật khẩu.")
    // SỬA: Đổi ApiResponse -> ResponseEntity cho chuẩn RESTful
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Đăng ký User mới. Yêu cầu xác thực Captcha.")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/social-login")
    @Operation(summary = "Đăng nhập Mạng xã hội", description = "Đăng nhập bằng Google/Facebook Token.")
    public ResponseEntity<LoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(authService.loginSocial(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu", description = "Gửi yêu cầu đặt lại mật khẩu kèm Captcha.")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("Yêu cầu thành công. Vui lòng kiểm tra Email hoặc SMS để đặt lại mật khẩu.");
    }
    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu", description = "Dùng token từ email để thiết lập mật khẩu mới.")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay bây giờ.");
    }
}