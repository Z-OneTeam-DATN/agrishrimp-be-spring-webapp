package com.zone.agri.controller;

import com.zone.agri.dto.address.UserAddressRequest;
import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Tag(name = "Address Book", description = "Quản lý sổ địa chỉ (Yêu cầu đăng nhập)")
@CrossOrigin(origins = "http://localhost:3000")
@SecurityRequirement(name = "bearerAuth")
public class UserAddressController {

    private final UserAddressService addressService;
    private final UserRepository userRepository;

    // --- HÀM BỔ TRỢ LẤY USER TỪ TOKEN ---

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("UNAUTHORIZED");
        }

        String contact = auth.getName();


        User user = userRepository.findByEmail(contact)
                .or(() -> userRepository.findByPhoneNumber(contact))
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại trong hệ thống"));

        return user.getId();
    }

    @Operation(summary = "Xem danh sách địa chỉ", description = "Lấy toàn bộ sổ địa chỉ của User đang đăng nhập")
    @GetMapping
    public ResponseEntity<?> getMyAddresses() {
        try {
            return ResponseEntity.ok(addressService.getUserAddresses(getCurrentUserId()));
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Thêm địa chỉ mới", description = "Tạo một địa chỉ nhận hàng mới")
    @PostMapping
    public ResponseEntity<?> addAddress(@RequestBody UserAddressRequest request) {
        try {
            addressService.addAddress(getCurrentUserId(), request);
            return ResponseEntity.ok(Map.of("message", "Thêm địa chỉ thành công"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật địa chỉ", description = "Chỉnh sửa thông tin địa chỉ đã có")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAddress(@PathVariable Long id, @RequestBody UserAddressRequest request) {
        try {
            addressService.updateAddress(getCurrentUserId(), id, request);
            return ResponseEntity.ok(Map.of("message", "Cập nhật địa chỉ thành công"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa địa chỉ", description = "Xóa một địa chỉ. Sẽ báo lỗi nếu đang cố xóa địa chỉ mặc định.")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(@PathVariable Long id) {
        try {
            addressService.deleteAddress(getCurrentUserId(), id);
            return ResponseEntity.ok(Map.of("message", "Xóa địa chỉ thành công"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Thiết lập địa chỉ mặc định", description = "Đưa một địa chỉ lên làm mặc định, tự động gỡ mặc định của địa chỉ cũ.")
    @PatchMapping("/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable Long id) {
        try {
            addressService.setDefaultAddress(getCurrentUserId(), id);
            return ResponseEntity.ok(Map.of("message", "Đã thiết lập mặc định"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}