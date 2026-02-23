package com.zone.agri.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Thông tin phản hồi chi tiết về vai trò")
public class RoleResponse {
    
    @Schema(description = "ID duy nhất của vai trò", example = "1")
    Long id;

    @Schema(description = "Tên hiển thị trên giao diện", example = "QUẢN TRỊ VIÊN")
    String displayName;

    @Schema(description = "Mã định danh duy nhất (Slug)", example = "ADMIN")
    String slug;

    @Schema(description = "Mô tả chi tiết nhiệm vụ", example = "Toàn quyền quản trị hệ thống")
    String description;

    @Schema(description = "Trạng thái hoạt động (true: Bật, false: Tắt)", example = "true")
    Boolean isActive;

    @Schema(description = "Đánh dấu vai trò mặc định của hệ thống (không được xóa/sửa)", example = "true")
    Boolean isSystem;

    @Schema(description = "Danh sách các mã quyền hạn (Permission Codes) được gán cho vai trò này", 
            example = "[\"DASHBOARD_VIEW\", \"USER_MANAGE\", \"PRODUCT_CREATE\"]")
    List<String> permissionCodes;
}
