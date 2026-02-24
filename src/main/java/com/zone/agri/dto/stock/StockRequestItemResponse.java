package com.zone.agri.dto.stock;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockRequestItemResponse {

    Long id;
    Long productVariantId;
    String sku;
    String productName;
    String unit;
    Integer requestedQty;
    Integer approvedQty;
}
