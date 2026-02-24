package com.zone.agri.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InventoryNoteDetailResponse {
    private Long id;
    private String sku;
    private String productName;
    private Integer quantityRequested;
    private BigDecimal price;
}
