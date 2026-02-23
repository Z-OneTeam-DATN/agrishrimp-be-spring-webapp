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
    private String receiptCode;
    private String supplierCode;
    private String branchName;
    private String deliverer;
    private String entryDate;
    private String note;
    private String importStatus; // "PO" hoặc "IMPORTED"
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