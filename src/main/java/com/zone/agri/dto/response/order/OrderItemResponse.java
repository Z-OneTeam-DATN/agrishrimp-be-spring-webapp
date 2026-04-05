package com.zone.agri.dto.response.order;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private Long productId; // Added
    private String productName;
    private String sku;
    private String image;
    private Integer quantity;
    private Integer allocatedQuantity;
    private Integer missingQuantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private boolean canReview; // Added
}
