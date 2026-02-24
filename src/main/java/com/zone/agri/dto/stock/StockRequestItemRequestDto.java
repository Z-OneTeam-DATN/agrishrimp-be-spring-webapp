package com.zone.agri.dto.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockRequestItemRequestDto {

    @NotNull(message = "productVariantId không được trống")
    Long productVariantId;

    @NotNull(message = "requestedQty không được trống")
    @Min(value = 1, message = "requestedQty phải >= 1")
    Integer requestedQty;
}
