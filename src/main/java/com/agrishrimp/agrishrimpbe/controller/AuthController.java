package com.agrishrimp.agrishrimpbe.controller;

import com.agrishrimp.agrishrimpbe.common.ApiResponse;
import com.agrishrimp.agrishrimpbe.dto.auth.LoginRequest;
import com.agrishrimp.agrishrimpbe.dto.auth.LoginResponse;
import com.agrishrimp.agrishrimpbe.dto.auth.RegisterRequest;
import com.agrishrimp.agrishrimpbe.dto.auth.SocialLoginRequest;
import com.agrishrimp.agrishrimpbe.model.User;
import com.agrishrimp.agrishrimpbe.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<User> register(@RequestBody RegisterRequest request) {
        User createdUser = authService.register(request);
        // Trả về thông tin user đã tạo (đã che pass trong Entity hoặc trả về DTO khác tùy bạn)
        return ApiResponse.success(createdUser);
    }

    @PostMapping("/social-login")
    public ApiResponse<LoginResponse> socialLogin(@RequestBody SocialLoginRequest request) {
        return ApiResponse.success(authService.loginSocial(request));
    }
}