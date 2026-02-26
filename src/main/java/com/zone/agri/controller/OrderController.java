package com.zone.agri.controller;

import com.zone.agri.dto.order.*;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "Quản lý Đặt hàng và Đơn hàng")
@CrossOrigin(origins = "http://localhost:3000")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để tiếp tục");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"))
                .getId();
    }

    @Operation(
            summary = "Đặt hàng (Checkout — legacy)",
            description = "Tạo đơn hàng COD tại 1 chi nhánh có đủ hàng. "
                    + "Đã thay thế bởi /prepare + /confirm."
    )
    @PostMapping("/orders/checkout")
    public ResponseEntity<?> placeOrder(@RequestBody CheckoutRequest request) {
        Long userId = getCurrentUserId();
        orderService.placeOrder(userId, request);
        return ResponseEntity.ok(Map.of("message", "Đặt hàng thành công!"));
    }

    // ── NEW: Tách đơn thông minh ──────────────────────────────────

    @Operation(
            summary = "Chuẩn bị đơn hàng",
            description = "Bước 1 — Tính toán phân bổ tồn kho + phí ship cho từng chi nhánh. "
                    + "Không lưu DB. Trả về prepareToken dùng cho /confirm (hết hạn sau 30 phút)."
    )
    @PostMapping("/orders/prepare")
    public ResponseEntity<PrepareOrderResponse> prepareOrder(
            @Valid @RequestBody PrepareOrderRequest request) {
        Long userId = getCurrentUserId();
        PrepareOrderResponse response = orderService.prepareOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Xác nhận đơn hàng",
            description = "Bước 2 — Lưu đơn vào DB, trừ tồn kho (có lock tránh race condition), "
                    + "tạo SubOrder theo từng chi nhánh."
    )
    @PostMapping("/orders/confirm")
    public ResponseEntity<ConfirmOrderResponse> confirmOrder(
            @Valid @RequestBody ConfirmOrderRequest request) {
        Long userId = getCurrentUserId();
        ConfirmOrderResponse response = orderService.confirmOrder(userId, request);
        log.info("Order confirmed for user {}: orderCode={}, checkoutUrl={}",
                userId, response.getOrderCode(), response.getCheckoutUrl());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy danh sách đơn hàng (Admin)", description = "Lấy toàn bộ danh sách đơn hàng cho trang quản trị")
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllOrders() {
        try {
            return ResponseEntity.ok(orderService.getAllAdminOrders());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi lấy danh sách đơn hàng: " + e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng", description = "Dành cho Admin duyệt đơn, đóng gói, giao hàng...")
    @PutMapping("/admin/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        try {
            orderService.updateOrderStatus(id, status);
            return ResponseEntity.ok(Map.of("message", "Cập nhật trạng thái thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy lịch sử đơn hàng của khách", description = "Admin xem nhật ký giao dịch của một khách hàng cụ thể.")
    @GetMapping("/admin/orders/user/{userId}")
    public ResponseEntity<?> getCustomerOrderHistory(@PathVariable Long userId) {
        List<Order> orders = orderService.getOrdersByUserId(userId);

        List<java.util.Map<String, Object>> responseList = orders.stream().map(order -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", order.getId());
            map.put("code", order.getCode());
            map.put("finalAmount", order.getFinalAmount());
            map.put("status", order.getStatus());
            map.put("createdAt", order.getCreatedAt());
            return map;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(responseList);
    }
}
