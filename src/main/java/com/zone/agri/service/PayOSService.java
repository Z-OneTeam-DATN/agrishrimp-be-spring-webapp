package com.zone.agri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zone.agri.dto.response.payment.PayOSApiResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.FulfillmentStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.StockStatus;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import vn.payos.PayOS;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
    private final SubOrderRepository subOrderRepository;
    private final ImmediateReplenishmentService immediateReplenishmentService;
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

    @Value("${order.auto-approve-minutes:5}")
    private long autoApproveMinutes;

    private static final String PAYOS_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    // ──────────────────────────────────────────────────────────────
    // Tạo link thanh toán — gọi REST API trực tiếp (không dùng SDK)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tạo PayOS payment link bằng cách gọi REST API trực tiếp.
     * Trả về PayOSLinkData chứa URL thanh toán.
     */
    public PayOSApiResponse.PayOSLinkData createPaymentLink(Order order) throws Exception {
        int amount = order.getFinalAmount().intValue();
        String description = truncate(order.getCode(), 25); // PayOS giới hạn 25 ký tự
        long orderCode = parseOrderCode(order);
        String resolvedReturnUrl = withOrderParams(returnUrl, order, "PAID");
        String resolvedCancelUrl = withOrderParams(cancelUrl, order, "CANCELLED");

        log.info("Creating PayOS payment link for order {}: orderCode={}, amount={}", order.getCode(), orderCode, amount);

        // Tính chữ ký theo chuẩn PayOS
        String dataToSign = "amount=" + amount
                + "&cancelUrl=" + resolvedCancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + resolvedReturnUrl;
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
        body.put("returnUrl", resolvedReturnUrl);
        body.put("cancelUrl", resolvedCancelUrl);
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
            String errMsg = apiResponse != null ? apiResponse.getDesc() : "null response";
            log.error("PayOS API error: {}", errMsg);
            throw new RuntimeException("PayOS API error: " + errMsg);
        }

        return apiResponse.getData();
    }

    /**
     * Chủ động kiểm tra trạng thái thanh toán từ PayOS (dùng khi Webhook chưa tới).
     */
    public boolean checkPaymentStatus(Order order) {
        try {
            long orderCode = parseOrderCode(order);
            String url = PAYOS_URL + "/" + orderCode;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            ResponseEntity<PayOSApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), PayOSApiResponse.class
            );

            PayOSApiResponse apiResponse = resp.getBody();
            if (apiResponse != null && "00".equals(apiResponse.getCode()) && apiResponse.getData() != null) {
                String status = apiResponse.getData().getStatus();
                return "PAID".equalsIgnoreCase(status);
            }
        } catch (Exception e) {
            log.warn("Failed to check PayOS status for order {}: {}", order.getCode(), e.getMessage());
        }
        return false;
    }

    public void cancelPaymentLink(Order order) {
        try {
            long orderCode = parseOrderCode(order);
            String url = PAYOS_URL + "/" + orderCode + "/cancel";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cancellationReason", "Khách hàng hoặc hệ thống hủy đơn");

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<PayOSApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), PayOSApiResponse.class
            );

            log.info("Đã hủy link PayOS cho đơn {}: {}", order.getCode(), resp.getBody() != null ? resp.getBody().getCode() : "unknown");
        } catch (Exception e) {
            log.warn("Lỗi khi hủy link PayOS cho đơn {}: {}", order.getCode(), e.getMessage());
        }
    }

    @Transactional
    public void handleWebhook(ObjectNode webhookBody) {
        try {
            Webhook webhookEnvelope = objectMapper.treeToValue(webhookBody, Webhook.class);
            WebhookData webhookData = payOS.verifyPaymentWebhookData(webhookEnvelope);

            if (!"00".equals(webhookData.getCode())) return;

            Long payosOrderCode = webhookData.getOrderCode();
            Optional<Order> orderOpt = orderRepository.findById(payosOrderCode);
            if (orderOpt.isEmpty()) {
                orderOpt = orderRepository.findByCode("ORD" + payosOrderCode);
            }
            if (orderOpt.isEmpty()) {
                String partialCode = String.valueOf(payosOrderCode);
                orderOpt = orderRepository.findAll().stream().filter(o -> o.getCode().contains(partialCode)).findFirst();
            }

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                if (!PaymentStatus.PAID.equals(order.getPaymentStatus())) {
                    markOrderPaid(order);
                    log.info("Order {} marked as PAID via Webhook", order.getCode());
                }
            }
        } catch (Exception e) {
            log.error("payOS webhook failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private long parseOrderCode(Order order) {
        try {
            return Long.parseLong(order.getCode().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return order.getId();
        }
    }

    private String hmacSHA256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    @Transactional
    public void markOrderPaid(Order order) {
        order.setPaymentStatus(PaymentStatus.PAID);

        List<SubOrder> subOrders = subOrderRepository.findByOrderId(order.getId());
        boolean hasAnyMissingItems = false;
        for (SubOrder subOrder : subOrders) {
            if (subOrder.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                boolean hasMissingItems = subOrder.getItems() != null
                        && subOrder.getItems().stream().anyMatch(item -> (item.getMissingQuantity() != null ? item.getMissingQuantity() : 0) > 0);
                subOrder.setStatus(OrderStatus.PENDING);
                hasAnyMissingItems = hasAnyMissingItems || hasMissingItems;
            }
        }

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            order.setStatus(OrderStatus.PENDING);
        }

        if (hasAnyMissingItems) {
            order.setAutoApproveAt(null);
            if (order.getStockStatus() == null) {
                order.setStockStatus(StockStatus.PARTIALLY_AVAILABLE);
            }
        } else {
            order.setAutoApproveAt(java.time.LocalDateTime.now().plusMinutes(Math.max(1, autoApproveMinutes)));
            order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED);
            if (order.getStockStatus() == null) {
                order.setStockStatus(StockStatus.FULLY_AVAILABLE);
            }
        }

        orderRepository.save(order);
        subOrderRepository.saveAll(subOrders);
    }

    private String withOrderParams(String baseUrl, Order order, String status) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
                + separator
                + "orderId=" + order.getId()
                + "&orderCode=" + urlEncode(order.getCode())
                + "&status=" + status;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
