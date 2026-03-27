package com.zone.agri.dto.request.order;

import lombok.Data;

@Data
public class CheckoutItemRequest {
    private Long variantId;
    private Integer quantity;
}
