package com.zone.agri.dto.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockRequestApproveItemDto {

    @NotNull(message = "itemId không được trống")
    Long itemId;

    @NotNull(message = "approvedQty không được trống")
    @Min(value = 0, message = "approvedQty phải >= 0")
    Integer approvedQty;
}
