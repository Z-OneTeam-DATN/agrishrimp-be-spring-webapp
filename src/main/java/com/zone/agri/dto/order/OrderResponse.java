package com.zone.agri.dto.order;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String code;
    private String customerName;
    private String customerPhone;
    private BigDecimal finalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String status;
    private String branchName;
    private LocalDateTime createdAt;
    private String shippingAddress;
    private String checkoutUrl;
    private List<OrderItemResponse> items;
}