package com.zone.agri.controller;

import com.zone.agri.dto.user.ProfileUpdateRequest;
import com.zone.agri.dto.user.UserRequest;
import com.zone.agri.dto.user.UserResponse;
import com.zone.agri.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Quản lý nhân viên, tài khoản và phân quyền hệ thống")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Danh sách nhân viên (Lọc & Phân trang)",
            description = "Lấy danh sách nhân viên có hỗ trợ tìm kiếm theo tên/email/sđt và lọc theo phòng ban/vai trò.")
//    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getUsers(
            @Parameter(description = "Từ khóa tìm kiếm (Tên, Email, SĐT, CCCD)", example = "012345678901")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Lọc theo ID vai trò", example = "1")
            @RequestParam(required = false) Long roleId,

            @Parameter(description = "Lọc theo ID chi nhánh", example = "1")
            @RequestParam(required = false) Long branchId,

            @Parameter(description = "Lọc theo trạng thái: all, active, inactive, banned", example = "active")
            @RequestParam(required = false, defaultValue = "all") String status,

            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getUsers(keyword, roleId, branchId, status, pageable));
    }

    @Operation(summary = "Lấy thông tin chi tiết nhân viên",
            description = "Trả về đầy đủ thông tin cá nhân, vai trò và chi nhánh của một nhân viên.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Thêm nhân viên mới",
            description = "Tạo tài khoản nhân viên mới, gán vai trò và chi nhánh. Mật khẩu mặc định là 123456 nếu không gửi lên.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công"),
            @ApiResponse(responseCode = "409", description = "Email hoặc Số điện thoại đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @Operation(summary = "Cập nhật thông tin nhân viên",
            description = "Chỉnh sửa thông tin cá nhân, thay đổi vai trò hoặc chuyển chi nhánh công tác.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng"),
            @ApiResponse(responseCode = "409", description = "Dữ liệu mới bị trùng với người dùng khác")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "Xóa nhân viên",
            description = "Xóa vĩnh viễn tài khoản nhân viên khỏi hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // API TỰ CẬP NHẬT PROFILE CHO USER
    // =========================================================
    @Operation(summary = "Tự cập nhật Profile cá nhân", description = "Dành cho User tự cập nhật thông tin của chính mình")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/edit-profile")
    public ResponseEntity<?> updateMyProfile(@RequestBody ProfileUpdateRequest request) {

        // 1. Lấy thông tin user đang đăng nhập từ Token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String contact = auth.getName();

        // 2. Chuyển cho UserService xử lý logic (Lưu ý: Bạn phải đảm bảo hàm updateMyProfile đã được thêm vào UserService.java như mình hướng dẫn ở bước trước nhé)
        userService.updateMyProfile(contact, request);

        return ResponseEntity.ok(Map.of("message", "Cập nhật thành công"));
    }
}