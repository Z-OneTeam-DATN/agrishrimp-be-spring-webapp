package com.zone.agri.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for creating a new employee/user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body để tạo nhân viên mới trong hệ thống")
public class EmployeeCreateRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Schema(description = "Họ và tên đầy đủ của nhân viên", example = "Nguyễn Văn An")
    private String fullName;

    @NotBlank(message = "Mã nhân viên không được để trống")
    @Pattern(regexp = "^NV-\\d{4,}$", message = "Mã nhân viên phải có định dạng NV-XXXX")
    @Schema(description = "Mã nhân viên duy nhất", example = "NV-1234")
    private String employeeCode;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email liên hệ", example = "nva@agrishrimp.com")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
    @Schema(description = "Số điện thoại 10 chữ số", example = "0901234567")
    private String phone;

    @Schema(description = "Địa chỉ thường trú", example = "123 Đường 3/2, Ninh Kiều, Cần Thơ")
    private String address;

    @NotNull(message = "Chi nhánh làm việc không được để trống")
    @Schema(description = "ID của chi nhánh", example = "1")
    private Long branchId;

    @NotNull(message = "Vai trò không được để trống")
    @Schema(description = "ID của vai trò/chức vụ", example = "2")
    private Long roleId;

    @Schema(description = "Ngày bắt đầu làm việc", example = "2024-01-15")
    private LocalDate startDate;

    @Schema(description = "URL ảnh đại diện (upload riêng)", example = "https://s3.amazonaws.com/avatar/employee123.jpg")
    private String avatarUrl;

    @Schema(description = "Trạng thái tài khoản", allowableValues = {"active", "locked"}, example = "active", defaultValue = "active")
    private String status;

    @Schema(description = "Mật khẩu mặc định (nếu không nhập sẽ tự sinh)", example = "Agri@2024")
    private String defaultPassword;
}
