package com.zone.agri.controller;

import com.zone.agri.dto.order.CheckoutRequest;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.OrderService;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "Quản lý Đặt hàng và Đơn hàng")
@CrossOrigin(origins = "http://localhost:3000")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("UNAUTHORIZED");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"))
                .getId();
    }

    @Operation(summary = "Đặt hàng (Checkout)", description = "Tạo đơn hàng COD mới. Hệ thống sẽ tự tìm chi nhánh có đủ tồn kho để trừ hàng.")
    @PostMapping("/checkout")
    public ResponseEntity<?> placeOrder(@RequestBody CheckoutRequest request) {
        try {
            orderService.placeOrder(getCurrentUserId(), request);
            return ResponseEntity.ok(Map.of("message", "Đặt hàng thành công!"));
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}