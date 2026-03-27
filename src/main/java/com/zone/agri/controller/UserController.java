package com.zone.agri.controller;

import com.zone.agri.dto.request.user.ProfileUpdateRequest;
import com.zone.agri.dto.request.user.UserRequest;
import com.zone.agri.dto.response.user.UserResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management")
public class UserController {

    private final UserService userService;

    // --- API CHO USER TỰ QUẢN LÝ ---

    @Operation(summary = "Tải ảnh đại diện", description = "Upload ảnh lên Cloudinary")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(file));
    }

    @Operation(summary = "Tự cập nhật Profile cá nhân", description = "Trả về dữ liệu mới nhất để Frontend đồng bộ UI")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/edit-profile")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String contact = auth.getName();

        // Trả về UserResponse thay vì Map để Frontend cập nhật Sidebar/Header ngay
        return ResponseEntity.ok(userService.updateMyProfile(contact, request));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("STAFF_VIEW")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getUsers(keyword, roleId, branchId, status, pageable));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("STAFF_VIEW")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("STAFF_CREATE")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("STAFF_UPDATE")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("STAFF_DELETE")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đổi mật khẩu cá nhân")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody com.zone.agri.dto.request.user.ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String contact = auth.getName();
        userService.changePassword(contact, request);

        return ResponseEntity.ok(Map.of(
                "message", "Đổi mật khẩu thành công",
                "status", "success"
        ));
    }
}