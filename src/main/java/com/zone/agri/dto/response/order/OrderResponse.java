package com.zone.agri.dto.response.order;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    /** SDT người nhận (từ UserAddress), khác customerPhone của tài khoản */
    private String receiverPhone;
    /** Tiền hàng chưa cộng phí ship */
    private BigDecimal totalAmount;
    /** Phí vận chuyển */
    private BigDecimal shippingFee;
    private BigDecimal totalShippingFee;
    private String voucherCode;
    private BigDecimal discountAmount;
    /** Tổng thanh toán = totalAmount + shippingFee - discount */
    private BigDecimal finalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String status;
    private String legacyStatus;
    private String fulfillmentStatus;
    private String stockStatus;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime autoApproveAt;
    private Boolean autoApprovalPaused;
    private String branchName;
    private String branchPhone;
    private String branchAddress;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    private String shippingAddress;
    private String note;
    private String cancelReasonCode;
    private String cancelReasonLabel;
    private String cancelReasonText;
    private String cancelReasonDisplay;
    private String checkoutUrl;
    private List<OrderItemResponse> items;
    private List<SubOrderSummaryDto> subOrders;
    private Boolean replenishmentRequested;
    private List<ReplenishmentPlanItem> replenishmentDocuments;
}
