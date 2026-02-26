package com.zone.agri.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareOrderRequest {
    private Double userLat;
    private Double userLng;

    @NotBlank(message = "Địa chỉ giao hàng là bắt buộc")
    private String deliveryAddress;

    @NotNull(message = "deliveryDistrictId là bắt buộc")
    private Integer deliveryDistrictId;

    private Integer deliveryProvinceId;

    @NotBlank(message = "deliveryWardCode là bắt buộc")
    private String deliveryWardCode;

    @NotEmpty(message = "Giỏ hàng không được rỗng")
    @Valid
    private List<CartItemDto> cart;
}
