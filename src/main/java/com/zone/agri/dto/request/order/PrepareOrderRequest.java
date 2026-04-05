package com.zone.agri.dto.request.order;

import com.zone.agri.dto.response.order.CartItemDto;
import jakarta.validation.Valid;
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

    @NotNull(message = "Vui lòng chọn một địa chỉ từ sổ địa chỉ")
    private Long userAddressId;

    private String voucherCode;

    @Valid
    private List<CartItemDto> cart;
}
