package com.zone.agri.controller;

import com.zone.agri.dto.request.order.*;
import com.zone.agri.dto.response.order.*;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.annotation.RequirePermission;
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
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để tiếp tục");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"));
    }

    @Operation(summary = "Lấy danh sách đơn hàng của tôi", description = "Người dùng xem lịch sử đơn hàng, có thể lọc theo trạng thái (PENDING, CONFIRMED...)")
    @GetMapping("/v1/orders/my-orders")
    public ResponseEntity<?> getMyOrders(@RequestParam(required = false) OrderStatus status) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(orderService.getMyOrders(userId, status));
    }

    @Operation(summary = "Lấy chi tiết đơn hàng của tôi", description = "Người dùng xem chi tiết thông tin và sản phẩm của một đơn hàng cụ thể")
    @GetMapping({"/v1/orders/{id}", "/orders/{id}"})
    public ResponseEntity<OrderResponse> getMyOrderDetail(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(orderService.getMyOrderDetail(userId, id));
    }

    @Operation(summary = "Khach hang xac nhan da nhan hang")
    @PostMapping("/v1/orders/{id}/confirm-received")
    public ResponseEntity<?> confirmReceivedByCustomer(@PathVariable Long id) {
        orderService.confirmReceivedByCustomer(getCurrentUserId(), id);
        return ResponseEntity.ok(Map.of("message", "Xác nhận nhận hàng thành công"));
    }

    @Operation(summary = "Hủy đơn hàng của tôi")
    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<?> cancelMyOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String reasonCode = body != null && body.get("reasonCode") != null ? body.get("reasonCode").toString() : null;
        String otherReasonText = body != null && body.get("otherReasonText") != null ? body.get("otherReasonText").toString() : null;
        String cancelReason = reasonCode;
        if (otherReasonText != null && !otherReasonText.isBlank()) {
            cancelReason = (cancelReason != null ? cancelReason + ": " : "") + otherReasonText;
        }
        orderService.cancelMyOrder(userId, id, cancelReason);
        return ResponseEntity.ok(Map.of("message", "Hủy đơn hàng thành công"));
    }

    @Operation(
            summary = "Đặt hàng (Checkout — legacy)",
            description = "Tạo đơn hàng COD tại 1 chi nhánh có đủ hàng. "
                    + "Đã thay thế bởi /prepare + /confirm."
    )
    @PostMapping("/orders/checkout")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody CheckoutRequest request) {
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

    @Operation(summary = "Lấy danh sách đơn hàng (Admin)", description = "Lấy toàn bộ đơn hàng, có thể lọc theo trạng thái và tìm kiếm mã đơn.")
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(orderService.getAdminOrders(status, search));
    }

    @Operation(summary = "Lấy chi tiết đơn hàng (Admin)", description = "Admin xem chi tiết toàn bộ thông tin đơn hàng")
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/admin/{id}")
    public ResponseEntity<OrderResponse> getAdminOrderDetail(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getAdminOrderDetail(id));
    }

    @Operation(summary = "Báo cáo nợ đơn", description = "Tổng hợp các sản phẩm đang thiếu hàng trong các phần đơn chờ nhập hàng.")
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/admin/backorders")
    public ResponseEntity<List<MissingItemReportDto>> getBackorderReport() {
        return ResponseEntity.ok(orderService.getBackorderReport());
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng (Admin)", description = "Admin duyệt đơn, đóng gói, giao hàng theo quy trình.")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(Map.of("message", "Cập nhật trạng thái '" + status + "' thành công!"));
    }

    @Operation(summary = "Lấy lịch sử đơn hàng của khách", description = "Admin xem nhật ký giao dịch của một khách hàng cụ thể.")
    @RequirePermission("ORDER_VIEW")
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

    // ── QUẢN LÝ ĐƠN HÀNG THEO CHI NHÁNH (Branch / Kho) ────────────

    @Operation(
            summary = "Danh sách đơn hàng của chi nhánh",
            description = "Nhân viên/quản lý chi nhánh xem danh sách phần đơn được phân bổ về chi nhánh mình. "
                    + "Có thể lọc theo trạng thái (status) và tìm kiếm theo mã đơn hoặc tên khách (search). "
                    + "Tài khoản phải được gán vào một chi nhánh (branch_id)."
    )
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/branch/orders")
    public ResponseEntity<List<BranchOrderResponse>> getBranchOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search) {
        User user = getCurrentUser();
        if (user.getBranch() == null) {
            throw new BadRequestException("Tài khoản chưa được gán vào chi nhánh nào");
        }
        return ResponseEntity.ok(orderService.getBranchOrders(user.getBranch().getId(), status, search));
    }

    @Operation(
            summary = "Chi tiết đơn hàng của chi nhánh",
            description = "Xem chi tiết phần đơn (SubOrder) thuộc chi nhánh của người dùng đang đăng nhập, "
                    + "bao gồm danh sách sản phẩm cần đóng gói và thông tin vận chuyển."
    )
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/branch/orders/{orderId}")
    public ResponseEntity<BranchOrderResponse> getBranchOrderDetail(@PathVariable Long orderId) {
        User user = getCurrentUser();
        if (user.getBranch() == null) {
            throw new BadRequestException("Tài khoản chưa được gán vào chi nhánh nào");
        }
        return ResponseEntity.ok(orderService.getBranchOrderDetail(user.getBranch().getId(), orderId));
    }

    @Operation(
            summary = "Cập nhật trạng thái phần đơn của chi nhánh",
            description = "Chi nhánh tự quản lý trạng thái phần đơn của mình theo quy trình: "
                    + "PENDING → CONFIRMED → PROCESSING → SHIPPING → COMPLETED. "
                    + "Trạng thái tổng của đơn hàng sẽ được tự động đồng bộ theo chi nhánh chậm nhất."
    )
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/branch/orders/{orderId}/status")
    public ResponseEntity<?> updateBranchSubOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {
        User user = getCurrentUser();
        if (user.getBranch() == null) {
            throw new BadRequestException("Tài khoản chưa được gán vào chi nhánh nào");
        }
        orderService.updateSubOrderStatus(user.getBranch().getId(), orderId, status);
        return ResponseEntity.ok(Map.of("message", "Cập nhật trạng thái '" + status + "' thành công!"));
    }

    @Operation(summary = "Tạo lệnh điều chuyển bổ sung cho đơn hàng")
    @RequirePermission("ORDER_UPDATE")
    @PostMapping("/admin/{id}/request-replenishment")
    public ResponseEntity<?> requestReplenishmentForAdmin(@PathVariable Long id) {
        List<String> transferCodes = orderService.requestReplenishmentForAdmin(id);
        return ResponseEntity.ok(Map.of(
                "message", "Đã tạo lệnh điều chuyển bổ sung",
                "transferCodes", transferCodes));
    }

    @Operation(summary = "Tạo lệnh điều chuyển bổ sung cho phần đơn của chi nhánh")
    @RequirePermission("ORDER_UPDATE")
    @PostMapping("/branch/orders/{orderId}/request-replenishment")
    public ResponseEntity<?> requestReplenishmentForBranch(@PathVariable Long orderId) {
        User user = getCurrentUser();
        if (user.getBranch() == null) {
            throw new BadRequestException("Tài khoản chưa được gán vào chi nhánh nào");
        }
        List<String> transferCodes = orderService.requestReplenishmentForBranch(user.getBranch().getId(), orderId);
        return ResponseEntity.ok(Map.of(
                "message", "Đã tạo lệnh điều chuyển bổ sung",
                "transferCodes", transferCodes));
    }
}
