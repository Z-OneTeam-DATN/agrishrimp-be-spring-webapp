package com.zone.agri.dto.response.inventory;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryIOSummaryResponse {
    private Long variantId;
    private String sku;
    private String productName;

    private Integer openingQuantity;
    private BigDecimal openingValue;

    private Integer importedQuantity;
    private BigDecimal importedValue;

    private Integer exportedQuantity;
    private BigDecimal exportedValue;

    private Integer closingQuantity;
    private BigDecimal closingValue;
}
