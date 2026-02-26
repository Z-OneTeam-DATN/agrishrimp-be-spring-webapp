package com.zone.agri.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {
    @NotNull(message = "productVariantId là bắt buộc")
    private Long productVariantId;

    @NotNull(message = "quantity là bắt buộc")
    @Min(value = 1, message = "Số lượng phải >= 1")
    private Integer quantity;
}
