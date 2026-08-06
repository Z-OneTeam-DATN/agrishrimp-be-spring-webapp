package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryInfoResponse {
    private Long totalItems;
    private Long lowStockCount;
    private Long outOfStockCount;
    private BigDecimal totalInventoryValue;

    private double valueChangePercent;
    private boolean valueIsNew;
}
