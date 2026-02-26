package com.zone.agri.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zone.agri.entity.Order;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.service.PayOSService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "payOS payment webhooks và tra cứu link thanh toán")
public class PaymentController {

    private final PayOSService payOSService;
    private final OrderRepository orderRepository;

    /**
     * payOS gọi endpoint này khi thanh toán hoàn tất.
     * Endpoint PHẢI public (không cần JWT) và LUÔN trả 200 để payOS không retry.
     */
    @Operation(summary = "payOS Webhook", description = "Nhận và xử lý webhook từ payOS khi thanh toán thành công")
    @PostMapping("/api/webhooks/payos")
    public ResponseEntity<Map<String, String>> handlePayOSWebhook(@RequestBody ObjectNode payload) {
        try {
            payOSService.handleWebhook(payload);
            return ResponseEntity.ok(Map.of("message", "OK"));
        } catch (Exception e) {
            log.error("payOS webhook error: {}", e.getMessage());
            // Still return 200 to prevent payOS retry storm; errors are logged above
            return ResponseEntity.ok(Map.of("message", "received"));
        }
    }

    /**
     * Cho phép user lấy lại checkout URL nếu họ cần mở lại trang thanh toán.
     */
    @Operation(summary = "Lấy link thanh toán payOS", description = "Trả về checkoutUrl của đơn hàng PAYOS (yêu cầu đăng nhập)")
    @GetMapping("/api/orders/{orderId}/payment-link")
    public ResponseEntity<Map<String, String>> getPaymentLink(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn hàng không tồn tại"));

        if (order.getPayosCheckoutUrl() == null) {
            throw new NotFoundException("Đơn hàng này không sử dụng thanh toán payOS");
        }

        return ResponseEntity.ok(Map.of("checkoutUrl", order.getPayosCheckoutUrl()));
    }
}
