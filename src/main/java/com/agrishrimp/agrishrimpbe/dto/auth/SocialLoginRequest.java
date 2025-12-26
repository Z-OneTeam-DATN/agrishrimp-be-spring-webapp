package com.agrishrimp.agrishrimpbe.dto.auth;

import lombok.Data;

@Data
public class SocialLoginRequest {
    // Token mà Frontend nhận được từ Google/Facebook
    private String token;

    // Loại mạng xã hội: "GOOGLE" hoặc "FACEBOOK"
    private String provider;
}