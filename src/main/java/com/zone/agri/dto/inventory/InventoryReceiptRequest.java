package com.zone.agri.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReceiptRequest {
    private String importType;     // "SUPPLIER" hoặc "INTERNAL"
    private Long sourceBranchId;   // Dùng khi INTERNAL (Kho xuất)

    private String receiptCode;
    private String supplierCode;   // Dùng khi SUPPLIER
    private String branchName;     // Kho nhận
    private String deliverer;
    private String entryDate;      // Ngày hẹn giao (yyyy-MM-dd)
    private String note;
    private String importStatus;   // "PO" (Phiếu tạm) hoặc "IMPORTED" (Đã nhập)

    private List<String> tags;     // React gửi lên mảng chuỗi
    private BigDecimal paymentAmount;
    private List<ItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        private String productCode; // SKU của variant
        private Integer plannedQuantity;
        private String lotNumber;
        private String expiryDate;
        private BigDecimal importPrice;
        private BigDecimal newSellingPrice;
    }
}