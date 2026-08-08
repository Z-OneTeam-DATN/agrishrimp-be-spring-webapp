package com.zone.agri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zone.agri.dto.request.order.PayOSCheckoutSession;
import com.zone.agri.dto.response.payment.PayOSApiResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.FulfillmentStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.StockStatus;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import vn.payos.PayOS;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSService {

    private static final String PAYOS_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    private final PayOS payOS;
    private final RestTemplate restTemplate;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
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

    public PayOSApiResponse.PayOSLinkData createPaymentLink(Order order) throws Exception {
        int amount = order.getFinalAmount().intValue();
        String description = truncate(order.getCode(), 25);
        long orderCode = parseOrderCode(order);
        String resolvedReturnUrl = withOrderParams(returnUrl, order, "PAID");
        String resolvedCancelUrl = resolveCancelCheckoutUrl(order);
        return createPaymentLink(orderCode, amount, description, resolvedReturnUrl, resolvedCancelUrl);
    }

    public PayOSApiResponse.PayOSLinkData createPaymentLink(
            PayOSCheckoutSession session,
            String description,
            String resolvedReturnUrl,
            String resolvedCancelUrl) throws Exception {
        if (session == null || session.getPayosOrderCode() == null || session.getTotalAmount() == null) {
            throw new IllegalArgumentException("Phien PayOS khong hop le de tao link thanh toan");
        }

        return createPaymentLink(
                session.getPayosOrderCode(),
                session.getTotalAmount().intValue(),
                description,
                resolvedReturnUrl,
                resolvedCancelUrl);
    }

    public PayOSApiResponse.PayOSLinkData createPaymentLink(
            long orderCode,
            int amount,
            String description,
            String resolvedReturnUrl,
            String resolvedCancelUrl) throws Exception {
        String safeDescription = truncate(description, 25);
        String dataToSign = "amount=" + amount
                + "&cancelUrl=" + resolvedCancelUrl
                + "&description=" + safeDescription
                + "&orderCode=" + orderCode
                + "&returnUrl=" + resolvedReturnUrl;
        String signature = hmacSHA256(dataToSign, checksumKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", safeDescription);
        body.put("items", List.of(Map.of(
                "name", "Don hang " + safeDescription,
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
                PayOSApiResponse.class);

        PayOSApiResponse apiResponse = resp.getBody();
        if (apiResponse == null || !"00".equals(apiResponse.getCode()) || apiResponse.getData() == null) {
            String errMsg = apiResponse != null ? apiResponse.getDesc() : "null response";
            log.error("PayOS API error: {}", errMsg);
            throw new RuntimeException("PayOS API error: " + errMsg);
        }

        return apiResponse.getData();
    }

    public boolean checkPaymentStatus(Order order) {
        if (order == null) {
            return false;
        }

        PayOSApiResponse.PayOSLinkData paymentData = fetchPaymentStatusData(parseOrderCode(order));
        if (paymentData == null || !"PAID".equalsIgnoreCase(paymentData.getStatus())) {
            return false;
        }

        if (!matchesExpectedAmount(order.getFinalAmount(), paymentData.getAmount())) {
            log.warn(
                    "PayOS amount mismatch for order {}: expected {}, actual {}",
                    order.getCode(),
                    order.getFinalAmount(),
                    paymentData.getAmount());
            return false;
        }

        return true;
    }

    public boolean checkPaymentStatus(long orderCode) {
        PayOSApiResponse.PayOSLinkData paymentData = fetchPaymentStatusData(orderCode);
        return paymentData != null && "PAID".equalsIgnoreCase(paymentData.getStatus());
    }

    private PayOSApiResponse.PayOSLinkData fetchPaymentStatusData(long orderCode) {
        try {
            String url = PAYOS_URL + "/" + orderCode;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            ResponseEntity<PayOSApiResponse> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PayOSApiResponse.class);

            PayOSApiResponse apiResponse = resp.getBody();
            if (apiResponse != null && "00".equals(apiResponse.getCode()) && apiResponse.getData() != null) {
                return apiResponse.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to check PayOS status for orderCode {}: {}", orderCode, e.getMessage());
        }
        return null;
    }

    public void cancelPaymentLink(Order order) {
        cancelPaymentLink(parseOrderCode(order));
    }

    public void cancelPaymentLink(long orderCode) {
        try {
            String url = PAYOS_URL + "/" + orderCode + "/cancel";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cancellationReason", "Khach hang hoac he thong huy don");

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<PayOSApiResponse> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    PayOSApiResponse.class);

            log.info("Cancelled PayOS link for orderCode {}: {}", orderCode,
                    resp.getBody() != null ? resp.getBody().getCode() : "unknown");
        } catch (Exception e) {
            log.warn("Failed to cancel PayOS link for orderCode {}: {}", orderCode, e.getMessage());
        }
    }

    public WebhookData verifyWebhook(ObjectNode webhookBody) {
        try {
            Webhook webhookEnvelope = objectMapper.treeToValue(webhookBody, Webhook.class);
            return payOS.verifyPaymentWebhookData(webhookEnvelope);
        } catch (Exception e) {
            log.error("payOS webhook failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void handleWebhook(ObjectNode webhookBody) {
        WebhookData webhookData = verifyWebhook(webhookBody);
        if (!"00".equals(webhookData.getCode())) {
            return;
        }

        Long payosOrderCode = webhookData.getOrderCode();
        Optional<Order> orderOpt = orderRepository.findById(payosOrderCode);
        if (orderOpt.isEmpty()) {
            orderOpt = orderRepository.findByCode("ORD" + payosOrderCode);
        }
        if (orderOpt.isEmpty()) {
            String partialCode = String.valueOf(payosOrderCode);
            orderOpt = orderRepository.findAll().stream()
                    .filter(o -> o.getCode() != null && o.getCode().contains(partialCode))
                    .findFirst();
        }

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (!matchesExpectedAmount(order.getFinalAmount(), webhookData != null ? webhookData.getAmount() : null)) {
                log.warn(
                        "Skip webhook for order {} because amount mismatch. Expected {}, actual {}",
                        order.getCode(),
                        order.getFinalAmount(),
                        webhookData != null ? webhookData.getAmount() : null);
                return;
            }

            if (!PaymentStatus.PAID.equals(order.getPaymentStatus())) {
                markOrderPaid(order);
                log.info("Order {} marked as PAID via Webhook", order.getCode());
            }
        }
    }

    @Transactional
    public void markOrderPaid(Order order) {
        order.setPaymentStatus(PaymentStatus.PAID);

        List<SubOrder> subOrders = subOrderRepository.findByOrderId(order.getId());
        boolean hasAnyMissingItems = false;
        for (SubOrder subOrder : subOrders) {
            if (subOrder.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                boolean hasMissingItems = subOrder.getItems() != null
                        && subOrder.getItems().stream()
                                .anyMatch(item -> (item.getMissingQuantity() != null ? item.getMissingQuantity() : 0) > 0);
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

    private long parseOrderCode(Order order) {
        try {
            return Long.parseLong(order.getCode().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return order.getId();
        }
    }

    private String withOrderParams(String baseUrl, Order order, String status) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
                + separator
                + "orderId=" + order.getId()
                + "&orderCode=" + urlEncode(order.getCode())
                + "&status=" + status;
    }

    private String resolveCancelCheckoutUrl(Order order) {
        String baseUrl = cancelUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = returnUrl;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://agrishrimp.io.vn/checkout";
        }

        String normalizedBaseUrl = baseUrl
                .replace("/order-cancel", "/checkout")
                .replace("/order-success", "/checkout");
        String separator = normalizedBaseUrl.contains("?") ? "&" : "?";
        return normalizedBaseUrl
                + separator
                + "resumeOrderId=" + order.getId()
                + "&orderId=" + order.getId()
                + "&orderCode=" + urlEncode(order.getCode())
                + "&status=CANCELLED"
                + "&paymentMethod=PAYOS";
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean matchesExpectedAmount(BigDecimal expectedAmount, Integer actualAmount) {
        if (expectedAmount == null || actualAmount == null) {
            return false;
        }

        return expectedAmount.stripTrailingZeros().compareTo(BigDecimal.valueOf(actualAmount).stripTrailingZeros()) == 0;
    }
}
