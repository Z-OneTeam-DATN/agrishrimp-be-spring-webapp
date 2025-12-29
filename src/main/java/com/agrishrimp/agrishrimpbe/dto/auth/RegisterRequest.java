package com.agrishrimp.agrishrimpbe.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 50, message = "Họ tên phải từ 2-50 ký tự")
    private String fullName;

    @NotBlank(message = "Email hoặc Số điện thoại không được để trống")
    private String identifier;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotNull(message = "Ngày sinh không được để trống")
    private LocalDate dateOfBirth;

    private Integer gender;
    private Boolean isNewsletterSubscribed;

    // Yêu cầu 2: Thêm Captcha
    private String captchaToken;
}