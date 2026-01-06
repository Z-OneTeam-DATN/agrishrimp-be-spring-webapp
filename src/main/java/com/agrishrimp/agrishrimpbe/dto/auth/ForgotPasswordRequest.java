package com.agrishrimp.agrishrimpbe.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Vui lòng nhập Email hoặc Số điện thoại")
    private String identifier;

    @NotBlank(message = "Vui lòng xác thực Captcha")
    private String captchaToken;
}