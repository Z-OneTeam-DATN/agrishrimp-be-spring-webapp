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
    private Integer quantity;
    private BigDecimal importPrice;
    private String shelfLocation;
    private LocalDateTime expiryDate;
    private String imageUrl;
}
