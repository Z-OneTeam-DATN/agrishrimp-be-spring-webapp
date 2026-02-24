package com.zone.agri.dto.branch;

import lombok.Data;

@Data
public class CheckStockItemRequest {
    private Long variantId;
    private Integer quantity;
}