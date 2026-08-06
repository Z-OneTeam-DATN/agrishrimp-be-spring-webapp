package com.zone.agri.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zone.agri.dto.request.order.RetryPendingPaymentRequest;
import com.zone.agri.dto.response.order.ConfirmOrderResponse;
import com.zone.agri.entity.User;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.OrderService;
import com.zone.agri.service.PayOSService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.type.WebhookData;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "payOS payment webhooks va thao tac thanh toan lai")
public class PaymentController {

    private final PayOSService payOSService;
    private final OrderService orderService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui long dang nhap de tiep tuc");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tai khoan khong ton tai"));
    }

    @Operation(summary = "payOS Webhook", description = "Nhan va xu ly webhook tu payOS khi thanh toan thanh cong")
    @PostMapping("/api/webhooks/payos")
    public ResponseEntity<Map<String, String>> handlePayOSWebhook(@RequestBody ObjectNode payload) {
        try {
            WebhookData webhookData = payOSService.verifyWebhook(payload);
            orderService.handlePayosWebhook(webhookData);
            return ResponseEntity.ok(Map.of("message", "OK"));
        } catch (Exception e) {
            log.error("payOS webhook error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("message", "received"));
        }
    }

    @Operation(summary = "Lay ket qua phien thanh toan payOS", description = "Kiem tra va hoan tat phien thanh toan PayOS. Neu da PAID thi moi tao don hang that.")
    @GetMapping("/api/payos/sessions/{sessionCode}/finalize")
    public ResponseEntity<ConfirmOrderResponse> finalizePayosSession(@PathVariable String sessionCode) {
        return ResponseEntity.ok(orderService.finalizePayosSession(getCurrentUserId(), sessionCode));
    }

    @Operation(summary = "Huy phien thanh toan payOS", description = "Danh dau phien PayOS da bi huy va dong link thanh toan de nguoi dung quay lai checkout chon lai.")
    @PostMapping("/api/payos/sessions/{sessionCode}/cancel")
    public ResponseEntity<Map<String, String>> cancelPayosSession(@PathVariable String sessionCode) {
        orderService.cancelPayosSession(getCurrentUserId(), sessionCode);
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @Operation(summary = "Lay link thanh toan payOS", description = "Tra ve checkoutUrl cua dung chu don hang dang cho thanh toan.")
    @GetMapping("/api/orders/{orderId}/payment-link")
    public ResponseEntity<Map<String, String>> getPaymentLink(@PathVariable Long orderId) {
        String checkoutUrl = orderService.getMyPayosPaymentLink(getCurrentUserId(), orderId);
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }

    @Operation(summary = "Chon lai phuong thuc thanh toan", description = "Mo lai PayOS hoac doi sang COD cho don dang cho thanh toan.")
    @PostMapping("/api/orders/{orderId}/retry-payment")
    public ResponseEntity<ConfirmOrderResponse> retryPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody RetryPendingPaymentRequest request) {
        return ResponseEntity.ok(orderService.retryPendingPayment(getCurrentUserId(), orderId, request));
    }
}
