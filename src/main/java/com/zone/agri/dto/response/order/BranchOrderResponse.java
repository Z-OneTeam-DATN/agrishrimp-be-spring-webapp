package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response trả về cho nhân viên/quản lý chi nhánh.
 * Mỗi bản ghi = 1 SubOrder (phần đơn thuộc chi nhánh đó) + thông tin đơn tổng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchOrderResponse {

    // ── Thông tin đơn tổng ──────────────────────────────────────
    private Long orderId;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private String paymentMethod;
    private String paymentStatus;
    /** Trạng thái tổng của đơn hàng (tổng hợp từ tất cả SubOrder) */
    private String orderStatus;

    // ── Thông tin phần đơn của chi nhánh (SubOrder) ─────────────
    private Long subOrderId;
    /** Trạng thái riêng của phần đơn thuộc chi nhánh này */
    private String subOrderStatus;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private String estimatedDays;
    private String carrier;
    private LocalDateTime statusUpdatedAt;
    private boolean shippingOverdue;
    private boolean canMarkReceived;
    private Long overdueShippingDays;

    /** Danh sách sản phẩm trong phần đơn của chi nhánh */
    private List<OrderItemResponse> items;
}
