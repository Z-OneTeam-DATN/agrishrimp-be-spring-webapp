package com.zone.agri.controller;

import com.zone.agri.dto.request.user.RoleRequest;
import com.zone.agri.dto.response.user.RoleResponse;
import com.zone.agri.entity.Role;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Các API quản lý vai trò và phân quyền hệ thống")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ROLE_CREATE")
    @Operation(
        summary = "Tạo vai trò mới",
        description = "Tạo một vai trò người dùng mới cùng với danh sách các quyền được chọn từ frontend.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Tạo thành công", 
                         content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc vai trò đã tồn tại")
        }
    )
    public ResponseEntity<RoleResponse> createRole(@jakarta.validation.Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(request));
    }

    @GetMapping
    @RequirePermission("ROLE_VIEW")
    @Operation(
        summary = "Danh sách vai trò (Lọc & Tìm kiếm)",
        description = "Hỗ trợ tìm kiếm theo tên/mã và lọc theo trạng thái/nguồn gốc vai trò. Kết quả trả về có phân trang."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<org.springframework.data.domain.Page<RoleResponse>> getAllRoles(
            @Parameter(description = "Từ khóa tìm kiếm (Tên hiển thị hoặc Slug)", example = "Thủ kho")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Lọc theo loại: all (tất cả), system (mặc định), custom (tự tạo)", example = "all")
            @RequestParam(required = false, defaultValue = "all") String type,

            @Parameter(description = "Lọc theo trạng thái: all (tất cả), active (đang hoạt động), inactive (tạm ngưng)", example = "active")
            @RequestParam(required = false, defaultValue = "all") String status,

            @Parameter(hidden = true)
            @org.springframework.data.web.PageableDefault(size = 10, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) 
            org.springframework.data.domain.Pageable pageable
    ) {
        return ResponseEntity.ok(roleService.getAllRoles(keyword, type, status, pageable));
    }

    @GetMapping("/permissions")
    @RequirePermission("ROLE_VIEW")
    @Operation(
        summary = "Lấy danh sách quyền hạn",
        description = "Trả về danh sách tất cả các quyền (Module và Action) có trong hệ thống để phục vụ việc phân quyền."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<com.zone.agri.entity.Permission>> getAllPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }

    @GetMapping("/{roleId}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ROLE_VIEW")
    @Operation(
        summary = "Lấy thông tin chi tiết vai trò",
        description = "Trả về thông tin đầy đủ của một vai trò cụ thể bao gồm danh sách quyền.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                         content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò")
        }
    )
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long roleId) {
        return ResponseEntity.ok(roleService.getRoleById(roleId));
    }

    @PutMapping("/{roleId}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ROLE_UPDATE")
    @Operation(
        summary = "Cập nhật vai trò",
        description = "Chỉnh sửa thông tin và quyền hạn của vai trò. Không cho phép sửa vai trò hệ thống (isSystem = true).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                         content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không được phép sửa vai trò hệ thống"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò"),
            @ApiResponse(responseCode = "409", description = "Tên vai trò đã tồn tại")
        }
    )
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable Long roleId,
            @jakarta.validation.Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(roleId, request));
    }

    @DeleteMapping("/{roleId}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ROLE_DELETE")
    @Operation(
        summary = "Xóa vai trò",
        description = "Xóa vai trò khỏi hệ thống. Không cho phép xóa vai trò hệ thống hoặc vai trò đang được sử dụng bởi người dùng.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @ApiResponse(responseCode = "403", description = "Không được phép xóa vai trò hệ thống"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò"),
            @ApiResponse(responseCode = "409", description = "Vai trò đang được sử dụng bởi người dùng")
        }
    )
    public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}
