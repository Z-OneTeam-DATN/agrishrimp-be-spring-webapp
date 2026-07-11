package com.zone.agri.dto.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Thông tin yêu cầu tạo mới hoặc cập nhật vai trò hệ thống")
public class RoleRequest {

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 100, message = "Tên vai trò tối đa 100 ký tự")
    @Schema(description = "Tên hiển thị của vai trò", example = "THỦ KHO TỔNG", requiredMode = Schema.RequiredMode.REQUIRED)
    String roleName;

    @Size(max = 255, message = "Mô tả vai trò tối đa 255 ký tự")
    @Schema(description = "Mô tả chi tiết nhiệm vụ của vai trò", example = "Chịu trách nhiệm nhập xuất và kiểm kê hàng hóa tại kho")
    String description;

    @Schema(description = "Trạng thái hoạt động (active: Hoạt động, inactive: Tạm ngưng)", allowableValues = {"active", "inactive"}, example = "active", defaultValue = "active")
    String status;

    @Schema(description = "Danh sách mã (code) các màn hình/module được phép truy cập", example = "[\"products\", \"categories\", \"receipts\"]")
    List<String> enabledScreens;

    @Schema(description = "Danh sách mã (code) các quyền thao tác nâng cao (duyệt, xóa, chốt sổ...)", example = "[\"receipt_approve\", \"inventory_audit_finalize\"]")
    List<String> advancedPerms;
}
