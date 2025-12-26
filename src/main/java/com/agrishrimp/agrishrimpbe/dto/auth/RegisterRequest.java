package com.agrishrimp.agrishrimpbe.dto.auth;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    // 1. Họ và tên
    private String fullName;

    // 2. Email hoặc Số điện thoại (Input chung)
    private String identifier;

    // 3. Mật khẩu
    private String password;

    // 4. Ngày sinh
    private LocalDate dateOfBirth;

    // 5. Giới tính (0: Nữ, 1: Nam - Theo UI của bạn Nữ đang được chọn)
    private Integer gender;

    // 6. Nhận thông tin khuyến mãi
    private Boolean isNewsletterSubscribed;

}