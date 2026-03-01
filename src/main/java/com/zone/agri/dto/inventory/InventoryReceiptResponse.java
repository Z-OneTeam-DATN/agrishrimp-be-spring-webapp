package com.zone.agri.dto.inventory;

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
public class InventoryReceiptResponse {
    private Long id;
    private String code;
    private String importType;     // Trả về cho Frontend biết loại gì
    private Long sourceBranchId;   // Trả về ID kho xuất (nếu có)
    private String status;
    private String supplierName;
    private String supplierCode;
    private String branchName;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal debtAmount;
    private String deliverer;
    private String note;
    private List<String> tags;     // Trả về mảng Tags
    private String entryDate;
    private LocalDateTime createdAt;
    private List<ItemResponse> items;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private String productCode;
        private String productName;
        private String unit;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal newSellingPrice;
        private String lotNumber;
        private String expiryDate;
        private String imageUrl;
        private Integer maxQuantity;
    }
}