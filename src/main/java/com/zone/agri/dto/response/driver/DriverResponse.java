package com.zone.agri.dto.response.driver;

import com.zone.agri.entity.Driver;
import com.zone.agri.entity.enums.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {
    private Long id;
    private String code;
    private String fullName;
    private String phone;
    private String email;
    private String idCard;
    private String licenseNumber;
    private String licenseClass;
    private String avatarUrl;
    private String licenseImageUrl;
    private DriverStatus status;
    private String vehicleNumber;
    private String vehicleType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdByUserId;
    private Long updatedByUserId;
    private String createdByName;
    private String updatedByName;

    public static DriverResponse fromEntity(Driver driver) {
        if (driver == null) return null;
        return DriverResponse.builder()
                .id(driver.getId())
                .code(driver.getCode())
                .fullName(driver.getFullName())
                .phone(driver.getPhone())
                .email(driver.getEmail())
                .idCard(driver.getIdCard())
                .licenseNumber(driver.getLicenseNumber())
                .licenseClass(driver.getLicenseClass())
                .avatarUrl(driver.getAvatarUrl())
                .licenseImageUrl(driver.getLicenseImageUrl())
                .status(driver.getStatus())
                .vehicleNumber(driver.getVehicleNumber())
                .vehicleType(driver.getVehicleType())
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .createdByUserId(driver.getCreatedByUserId())
                .updatedByUserId(driver.getUpdatedByUserId())
                .build();
    }
}
