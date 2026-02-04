package com.zone.agri.dto.user;

import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserOutDto {

    Long id;

    String fullName;

    String email;

    String phoneNumber;

    LocalDate dateOfBirth;

    String avatarUrl;

    Gender gender;

    UserStatus status;

    // --- QUAN HỆ ---

    // 1. Role: Trả về Object RoleDto (để FE lấy được cả slug lẫn displayName)
    RoleDto role;

    // 2. Branch: Tạm thời trả về ID.
    // Nếu muốn hiển thị tên chi nhánh thì thêm field `branchName`
    Long branchId;

    // --- METADATA ---
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    // --- HÀM TIỆN ÍCH (HELPER) ---
    // Giúp bạn convert nhanh từ Entity -> DTO ngay tại đây cho gọn code Service
    public static UserOutDto from(User user) {
        if (user == null) return null;

        // Xử lý Role (tránh null)
        RoleDto roleDto = (user.getRole() != null) ? new RoleDto(user.getRole()) : null;

        return UserOutDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .status(user.getStatus())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .role(roleDto)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}