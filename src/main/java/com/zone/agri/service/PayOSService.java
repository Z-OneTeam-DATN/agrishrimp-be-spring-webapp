package com.zone.agri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zone.agri.dto.payment.PayOSApiResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSService {

    /** Giữ SDK để verify webhook signature */
    private final PayOS payOS;
    private final RestTemplate restTemplate;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    private static final String PAYOS_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    // ──────────────────────────────────────────────────────────────
    // Tạo link thanh toán — gọi REST API trực tiếp (không dùng SDK)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tạo PayOS payment link bằng cách gọi REST API trực tiếp.
     * Không dùng payOS.createPaymentLink() để tránh bug signature verification trong SDK 1.0.3+.
     */
    public CheckoutResponseData createPaymentLink(Order order) throws Exception {
        int amount = order.getFinalAmount().intValue();
        String description = truncate(order.getCode(), 25); // PayOS giới hạn 25 ký tự
        long orderCode = parseOrderCode(order);

        log.info("Creating PayOS payment link for order {}: orderCode={}, amount={}", order.getCode(), orderCode, amount);

        // Tính chữ ký theo chuẩn PayOS:
        // sorted alphabetically: amount, cancelUrl, description, orderCode, returnUrl
        String dataToSign = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        String signature = hmacSHA256(dataToSign, checksumKey);

        // Build request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("items", List.of(Map.of(
                "name", "Đơn hàng " + description,
                "quantity", 1,
                "price", amount
        )));
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<PayOSApiResponse> resp = restTemplate.exchange(
                PAYOS_URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                PayOSApiResponse.class
        );

        PayOSApiResponse apiResponse = resp.getBody();
        if (apiResponse == null || !"00".equals(apiResponse.getCode()) || apiResponse.getData() == null) {
            String errMsg = apiResponse != null
                    ? "code=" + apiResponse.getCode() + " desc=" + apiResponse.getDesc()
                    : "null response";
            log.error("PayOS API error for order {}: {}", order.getCode(), errMsg);
            throw new RuntimeException("PayOS API error: " + errMsg);
        }

        PayOSApiResponse.PayOSLinkData data = apiResponse.getData();
        if (data.getCheckoutUrl() == null) {
            throw new RuntimeException("PayOS returned null checkoutUrl");
        }

        log.info("PayOS link created for order {}: {}", order.getCode(), data.getCheckoutUrl());

        // Map sang CheckoutResponseData để tương thích với OrderService
        return CheckoutResponseData.builder()
                .paymentLinkId(data.getPaymentLinkId())
                .checkoutUrl(data.getCheckoutUrl())
                .bin(data.getBin())
                .accountNumber(data.getAccountNumber())
                .accountName(data.getAccountName())
                .amount(data.getAmount())
                .description(data.getDescription())
                .orderCode(data.getOrderCode())
                .currency(data.getCurrency())
                .status(data.getStatus())
                .qrCode(data.getQrCode())
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    // Xử lý webhook — vẫn dùng SDK để verify signature
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(ObjectNode webhookBody) {
        try {
            Webhook webhookEnvelope = objectMapper.treeToValue(webhookBody, Webhook.class);
            WebhookData webhookData = payOS.verifyPaymentWebhookData(webhookEnvelope);

            if (!"00".equals(webhookData.getCode())) {
                log.info("payOS webhook: non-success transaction code={}", webhookData.getCode());
                return;
            }

            Long payosOrderCode = webhookData.getOrderCode();
            Optional<Order> orderOpt = orderRepository.findById(payosOrderCode);
            if (orderOpt.isEmpty()) {
                orderOpt = orderRepository.findByCode("ORD" + payosOrderCode);
            }

            if (orderOpt.isEmpty()) {
                log.warn("payOS webhook: order not found for orderCode={}", payosOrderCode);
                return;
            }

            Order order = orderOpt.get();
            if (PaymentStatus.PAID.equals(order.getPaymentStatus())) {
                log.info("payOS webhook: order {} already PAID, skipping", payosOrderCode);
                return;
            }

            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
            log.info("payOS webhook: order {} marked as PAID", payosOrderCode);

        } catch (Exception e) {
            log.error("payOS webhook processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook verification failed: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private long parseOrderCode(Order order) {
        try {
            return Long.parseLong(order.getCode().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse numeric orderCode from {}, using order.id", order.getCode());
            return order.getId();
        }
    }

    private String hmacSHA256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
