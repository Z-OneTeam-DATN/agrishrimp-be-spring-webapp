package com.zone.agri.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email không được để trống")
    private String contact;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Captcha token không được để trống")
    private String captchaToken;
}
