package com.zone.agri.dto.response.employee;

import com.zone.agri.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for employee response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của nhân viên")
public class EmployeeResponse {

    @Schema(description = "ID nhân viên")
    private Long id;

    @Schema(description = "Họ và tên đầy đủ")
    private String fullName;

    @Schema(description = "Mã nhân viên")
    private String employeeCode;

    @Schema(description = "Email liên hệ")
    private String email;

    @Schema(description = "Số điện thoại")
    private String phoneNumber;

    @Schema(description = "Số CCCD/CMND")
    private String citizenId;

    @Schema(description = "Địa chỉ thường trú")
    private String addressDetail;

    @Schema(description = "Ngày sinh")
    private LocalDate dateOfBirth;

    @Schema(description = "URL ảnh đại diện")
    private String avatarUrl;

    @Schema(description = "Trạng thái tài khoản")
    private UserStatus status;

    @Schema(description = "Ngày bắt đầu làm việc")
    private LocalDate startDate;

    @Schema(description = "Thông tin chi nhánh")
    private BranchInfo branch;

    @Schema(description = "Thông tin vai trò")
    private RoleInfo role;

    @Schema(description = "Ngày tạo")
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchInfo {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private Long id;
        private String displayName;
        private String slug;
    }
}
