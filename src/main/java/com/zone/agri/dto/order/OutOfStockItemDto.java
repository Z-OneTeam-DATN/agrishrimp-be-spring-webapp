package com.zone.agri.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutOfStockItemDto {
    private Long productVariantId;
    private String variantName;
    private String variantSku;
    private Integer requestedQty;
    private Integer availableQty;
}
