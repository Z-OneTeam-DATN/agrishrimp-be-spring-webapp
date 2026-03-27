package com.zone.agri.dto.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Thông tin yêu cầu tạo mới hoặc cập nhật người dùng hệ thống")
public class UserRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Schema(description = "Họ và tên đầy đủ của nhân viên", example = "Nguyễn Văn A", requiredMode = Schema.RequiredMode.REQUIRED)
    String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Schema(description = "Địa chỉ email (dùng làm tên đăng nhập)", example = "vana@agrishrimp.vn", requiredMode = Schema.RequiredMode.REQUIRED)
    String email;

    @Schema(description = "Mật khẩu đăng nhập (Mặc định: 123456)", example = "123456")
    @Size(min = 6, message = "Mật khẩu phải từ 6 ký tự")
    String password;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Schema(description = "Số điện thoại liên lạc", example = "0987654321", requiredMode = Schema.RequiredMode.REQUIRED)
    String phoneNumber;

    @Schema(description = "Số Căn cước công dân (12 chữ số)", example = "012345678901")
    @Size(min = 12, max = 12, message = "Số CCCD phải đủ 12 số")
    String citizenId;

    @Schema(description = "Ngày sinh của nhân viên (Định dạng: YYYY-MM-DD)", example = "1995-05-20")
    LocalDate dateOfBirth;

    @Schema(description = "ID của Vai trò (Lấy từ danh sách Roles)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    Long roleId;

    @Schema(description = "ID của Chi nhánh làm việc (Lấy từ danh sách Branches)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    Long branchId;

    @Schema(description = "Giới tính (0: Nữ, 1: Nam, 2: Khác)", example = "1", allowableValues = {"0", "1", "2"})
    Integer gender;

    @Schema(description = "Trạng thái tài khoản", allowableValues = {"ACTIVE", "INACTIVE", "BANNED"}, example = "ACTIVE")
    String status;

    @Schema(description = "URL ảnh đại diện của người dùng", example = "https://res.cloudinary.com/avatar.png")
    String avatarUrl;
}
