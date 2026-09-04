package com.zone.agri.dto.response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockReportResponse {
    private Long variantId;
    private Long branchId;
    private String branchName;
    private String sku;
    private String productName;
    private String unit;
    private Integer quantity;
    private Integer minThreshold;
    private Integer shortage;
    private boolean isLowStock;
    private LocalDateTime lastImportDate;
    private Integer mainBranchQuantity;
}
