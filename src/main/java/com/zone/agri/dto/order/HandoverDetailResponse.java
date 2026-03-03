package com.zone.agri.dto.order;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HandoverDetailResponse {
    private Long id;
    private String code;
    private String carrier;
    private Integer totalOrders;
    private Double totalWeight;
    private BigDecimal totalCod;
    private String status;
    private String creatorName;
    private String branchAddress; // Thêm cái này để in biên bản
    private LocalDateTime createdAt;
    private List<SubOrderHandoverItem> subOrders; // Danh sách đơn hàng bên trong

    @Data
    @Builder
    public static class SubOrderHandoverItem {
        private Long id;
        private String orderCode;
        private String customerName;
        private String shippingAddress;
        private String trackingCode;
        private BigDecimal subtotal;
        private String paymentStatus;
    }
}