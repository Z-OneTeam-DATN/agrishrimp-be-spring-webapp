package com.zone.agri.dto.request.driver;

import com.zone.agri.entity.enums.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DriverRequest {
    private String code;

    @NotBlank(message = "Họ tên tài xế không được để trống")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^$|^(0|84)[3|5|7|8|9][0-9]{8}$|^0[\\d\\s\\-]{8,19}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Pattern(regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Số CCCD không được để trống")
    private String idCard;

    @NotBlank(message = "Số bằng lái không được để trống")
    private String licenseNumber;

    @NotBlank(message = "Hạng bằng lái không được để trống")
    private String licenseClass;

    private String avatarUrl;
    private String licenseImageUrl;

    @NotNull(message = "Trạng thái không được để trống")
    private DriverStatus status;

    private String vehicleNumber;
    private String vehicleType;
}
