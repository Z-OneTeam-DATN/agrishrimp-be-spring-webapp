package com.zone.agri.dto.customer;

import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    // Thông tin từ bảng User (Tài khoản gốc)
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private AuthProvider provider; // Biết được tạo qua LOCAL (Admin tạo giùm) hay GOOGLE (Khách tự đăng nhập)
    private UserStatus userStatus;
    private LocalDateTime createdAt;

    // Thông tin từ bảng Customer (Hồ sơ thêm, có thể null nếu khách chỉ mới login Google)
    private Long customerId;
    private CustomerStatus customerStatus;
    private String addressDetail;
}