package com.zone.agri.dto.response.inventory;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLedgerEntryResponse {
    private Long id;
    private LocalDateTime createdAt;
    private String productName;
    private String sku;
    private String type;
    private Integer quantityChange;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String branchName;
    private String createdByName;
    private String reason;
}
