package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingItemReportDto {
    private Long productVariantId;
    private String sku;
    private String productName;
    private String variantName;
    private String imageUrl;
    private Integer totalMissingQuantity;
    private Long affectedSubOrders;
}
