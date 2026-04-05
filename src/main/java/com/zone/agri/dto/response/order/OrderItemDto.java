package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {
    private Long productVariantId;
    private String variantName;
    private String variantSku;
    private Integer quantity;
    private Integer allocatedQuantity;
    private Integer missingQuantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
