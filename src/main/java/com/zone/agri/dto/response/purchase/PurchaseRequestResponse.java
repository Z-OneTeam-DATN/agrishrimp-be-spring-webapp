package com.zone.agri.dto.response.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestResponse {

    private Long id;
    private String code;
    private String status;          // PurchaseRequestStatus.name()

    // Nhà cung cấp
    private Long supplierId;
    private String supplierName;
    private String supplierCode;

    // Chi nhánh nhận hàng
    private Long branchId;
    private String branchName;

    // Thời gian
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expectedDeliveryDate;
    private LocalDateTime approvedAt;
    private LocalDateTime sentToSupplierAt;
    private LocalDateTime completedAt;

    // Người thực hiện
    private String createdByName;
    private String approvedByName;

    // Tài chính
    private BigDecimal totalAmount;

    private String note;

    // Thống kê phiếu nhập liên kết
    private long totalReceiptCount;     // Tổng số phiếu nhập đã tạo
    private long completedReceiptCount; // Số phiếu nhập đã hoàn tất QC

    // Dòng hàng hóa
    private List<ItemResponse> items;

    // Lịch sử đợt nhập
    private List<GoodsReceiptSummary> goodsReceipts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private Long id;
        private Long productVariantId;
        private String productCode;     // SKU
        private String productName;
        private String imageUrl;
        private Integer requestedQty;   // Yêu cầu
        private Integer deliveredQty;   // NCC đã giao (lũy kế)
        private Integer acceptedQty;    // Đạt QC (cộng kho)
        private Integer defectiveQty;   // Lỗi (kho lỗi)
        private Integer remainingQty;   // Còn thiếu
        private BigDecimal unitPrice;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoodsReceiptSummary {
        private Long id;
        private String code;
        private String status;
        private LocalDateTime createdAt;
        private String createdByName;
        private Integer totalDelivered;     // Tổng NCC giao trong đợt này
        private Integer totalAccepted;      // Tổng đạt QC trong đợt này
        private Integer totalDefective;     // Tổng lỗi trong đợt này
    }
}
