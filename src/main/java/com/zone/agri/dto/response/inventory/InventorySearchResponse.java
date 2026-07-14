package com.zone.agri.dto.response.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySearchResponse {
    private Long variantId;
    private String productName;
    private String variantName;
    private String sku;
    private String barcode;
    private String batchNumber;
    private Integer quantity; // Tồn tốt hiện tại
    private Integer defectiveQuantity; // SL lỗi hiện tại
    private Integer plannedQuantity; // SL yêu cầu lúc nhập
    private String reason; // Lý do lỗi lúc nhập
    private BigDecimal importPrice;
    private String shelfLocation;
    private LocalDateTime expiryDate;
    private String imageUrl;
    private String unit;
    private Long supplierId;
    private String supplierName;
    private Long receiptId;
    private String receiptCode;
    private LocalDateTime receiptDate;
    private String branchName;

    public InventorySearchResponse(
            Long variantId,
            String productName,
            String variantName,
            String sku,
            String barcode,
            String batchNumber,
            Integer quantity,
            Integer defectiveQuantity,
            Integer plannedQuantity,
            String reason,
            BigDecimal importPrice,
            String shelfLocation,
            LocalDateTime expiryDate,
            String imageUrl,
            String unit
    ) {
        this.variantId = variantId;
        this.productName = productName;
        this.variantName = variantName;
        this.sku = sku;
        this.barcode = barcode;
        this.batchNumber = batchNumber;
        this.quantity = quantity;
        this.defectiveQuantity = defectiveQuantity;
        this.plannedQuantity = plannedQuantity;
        this.reason = reason;
        this.importPrice = importPrice;
        this.shelfLocation = shelfLocation;
        this.expiryDate = expiryDate;
        this.imageUrl = imageUrl;
        this.unit = unit;
    }
}
