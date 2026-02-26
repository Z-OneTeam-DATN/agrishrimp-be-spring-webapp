package com.zone.agri.dto.order;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private String productName;
    private String sku;
    private String image;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
}