package com.agrishrimp.agrishrimpbe.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Tài khoản không được để trống")
    private String identifier;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    // Yêu cầu 2: Thêm Captcha token từ Frontend gửi lên
    @NotBlank(message = "Captcha token không được để trống")
    private String captchaToken;
}