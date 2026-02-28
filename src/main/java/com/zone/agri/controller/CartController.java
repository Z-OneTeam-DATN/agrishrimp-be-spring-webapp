package com.zone.agri.controller;

import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart Management", description = "Quản lý giỏ hàng của người dùng (Yêu cầu đăng nhập)")
public class CartController {

    private final CartService cartService;
    private final UserRepository userRepository;

    // --- HÀM BỔ TRỢ LẤY USER TỪ TOKEN ---
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Nếu không có token, chưa đăng nhập, hoặc là tài khoản vô danh (anonymous)
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("UNAUTHORIZED"); // Bắn tín hiệu để React biết
        }

        String email = auth.getName(); // Spring Security lưu email của user ở đây
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại trong hệ thống"));

        return user.getId();
    }

    // --- 1. LẤY GIỎ HÀNG ---
    @Operation(summary = "Xem giỏ hàng cá nhân", description = "Lấy danh sách chi tiết các sản phẩm đang có trong giỏ hàng của người dùng hiện tại (dựa vào JWT Token).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu thành công"),
            @ApiResponse(responseCode = "401", description = "Lỗi xác thực (Vui lòng đăng nhập)")
    })
    @GetMapping
    public ResponseEntity<?> getMyCart() {
        try {
            return ResponseEntity.ok(cartService.getMyCart(getCurrentUserId()));
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // --- 2. THÊM / SỬA SỐ LƯỢNG GIỎ HÀNG ---
    @Operation(summary = "Cập nhật số lượng sản phẩm", description = "Thêm sản phẩm mới vào giỏ hoặc tăng/giảm số lượng sản phẩm đã có. Truyền delta = 1 để tăng, delta = -1 để giảm. Nếu số lượng <= 0, tự động xóa khỏi giỏ.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật giỏ hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi nghiệp vụ (Vượt quá số lượng tồn kho, Không tìm thấy sản phẩm...)"),
            @ApiResponse(responseCode = "401", description = "Lỗi xác thực (Vui lòng đăng nhập)")
    })
    @PostMapping("/update")
    public ResponseEntity<?> updateQuantity(
            @Parameter(description = "ID của phân loại sản phẩm (Variant ID)", example = "5", required = true)
            @RequestParam Long variantId,

            @Parameter(description = "Số lượng thay đổi (Ví dụ: 1 để cộng thêm, -1 để trừ đi)", example = "1", required = true)
            @RequestParam Integer delta) {
        try {
            cartService.updateCartQuantity(getCurrentUserId(), variantId, delta);
            return ResponseEntity.ok(Map.of("message", "Đã cập nhật giỏ hàng"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // --- 3. XÓA SẢN PHẨM KHỎI GIỎ ---
    @Operation(summary = "Xóa sản phẩm khỏi giỏ", description = "Xóa hoàn toàn một mục (CartItem) ra khỏi giỏ hàng của người dùng.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xóa sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi (Không tìm thấy mục trong giỏ, hoặc cố ý xóa giỏ hàng của người khác)"),
            @ApiResponse(responseCode = "401", description = "Lỗi xác thực (Vui lòng đăng nhập)")
    })
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<?> removeItem(
            @Parameter(description = "ID của dòng giỏ hàng cần xóa (CartItem ID)", example = "12", required = true)
            @PathVariable Long cartItemId) {
        try {
            cartService.removeCartItem(getCurrentUserId(), cartItemId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa khỏi giỏ hàng"));
        } catch (Exception e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}