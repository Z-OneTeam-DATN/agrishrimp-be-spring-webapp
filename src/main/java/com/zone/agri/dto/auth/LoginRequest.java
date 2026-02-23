package com.zone.agri.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email hoặc SĐT không được để trống")
    private String contact;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String captchaToken;
}
