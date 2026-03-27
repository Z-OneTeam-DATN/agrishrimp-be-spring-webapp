package com.zone.agri.dto.response.user;

import com.zone.agri.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Thông tin chi tiết người dùng/nhân viên trả về")
public class UserResponse {

    @Schema(description = "ID duy nhất của người dùng", example = "10")
    Long id;

    @Schema(description = "Họ và tên đầy đủ", example = "Nguyễn Văn A")
    String fullName;

    @Schema(description = "Địa chỉ Email", example = "vana@agrishrimp.vn")
    String email;

    @Schema(description = "Số điện thoại", example = "0987654321")
    String phoneNumber;

    @Schema(description = "Số Căn cước công dân", example = "012345678901")
    String citizenId;

    @Schema(description = "Ngày sinh", example = "1995-05-20")
    LocalDate dateOfBirth;

    @Schema(description = "URL ảnh đại diện", example = "https://ui-avatars.com/api/?name=Admin")
    String avatarUrl;

    @Schema(description = "Giới tính (MALE, FEMALE, OTHER)", example = "MALE")
    String gender;

    @Schema(description = "Trạng thái hiện tại của tài khoản", example = "ACTIVE")
    UserStatus status;

    @Schema(description = "Tên vai trò được gán", example = "QUẢN TRỊ VIÊN")
    String roleName;

    @Schema(description = "ID của vai trò", example = "1")
    Long roleId;

    @Schema(description = "Tên chi nhánh/kho đang làm việc", example = "Trụ sở chính Cần Thơ")
    String branchName;

    @Schema(description = "ID của chi nhánh", example = "1")
    Long branchId;

    @Schema(description = "Thời điểm tạo tài khoản", example = "2026-02-15T14:30:00")
    LocalDateTime createdAt;


}
