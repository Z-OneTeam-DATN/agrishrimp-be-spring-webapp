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
public class StockSummaryResponse {
    private Long variantId;
    private String sku;
    private String productName;
    private String categoryName;

    private Integer branchQuantity;
    private BigDecimal branchValue;
    private BigDecimal branchAvgCost;
    private BigDecimal branchWeightPercent;

    private Integer systemQuantity;
    private BigDecimal systemValue;
}
