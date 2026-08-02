package com.zone.agri.dto.request.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOSCheckoutSession {
    private String sessionCode;
    private Long payosOrderCode;
    private String prepareToken;
    private Long userId;
    private String idempotencyKey;
    private String note;
    private String status;
    private String checkoutUrl;
    private String paymentLinkId;
    private Long orderId;
    private String orderCode;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalShippingFee;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private PrepareOrderDraft draftSnapshot;
}
