package com.zone.agri.dto.response.order;

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
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private String receiverName;
    /** SĐT người nhận (từ UserAddress) — khác customerPhone (SĐT tài khoản) */
    private String receiverPhone;
    /** Tiền hàng (chưa cộng phí ship) */
    private BigDecimal totalAmount;
    /** Phí vận chuyển */
    private BigDecimal shippingFee;
    private BigDecimal totalShippingFee;
    /** Tổng thanh toán = totalAmount + shippingFee - discount */
    private BigDecimal finalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String status;
    private String branchName;
    private String branchPhone;
    private String branchAddress;
    private LocalDateTime createdAt;
    private String shippingAddress;
    private String note;
    private String checkoutUrl;
    private List<OrderItemResponse> items;
    private List<SubOrderSummaryDto> subOrders;
}
