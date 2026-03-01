package com.zone.agri.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

    // Optional: chọn từ sổ địa chỉ đã lưu
    private Long userAddressId;

    // Bắt buộc khi không có userAddressId
    private String receiverName;
    private String receiverPhone;

    // Bắt buộc khi không có userAddressId (validation làm trong service)
    private String deliveryAddress;
    private Integer deliveryDistrictId;
    private Integer deliveryProvinceId;
    private String deliveryWardCode;

    @NotEmpty(message = "Giỏ hàng không được rỗng")
    @Valid
    private List<CartItemDto> cart;
}
