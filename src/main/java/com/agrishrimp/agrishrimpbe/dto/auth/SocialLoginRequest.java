package com.agrishrimp.agrishrimpbe.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialLoginRequest {
    @NotBlank(message = "Token không được để trống")
    private String token;

    @NotBlank(message = "Provider không được để trống")
    private String provider;
}