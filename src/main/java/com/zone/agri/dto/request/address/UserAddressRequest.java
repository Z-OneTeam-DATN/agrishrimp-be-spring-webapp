package com.zone.agri.dto.request.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserAddressRequest {
    @NotBlank(message = "Tên người nhận không được để trống")
    private String receiverName;

    @NotBlank(message = "Số điện thoại người nhận không được để trống")
    private String receiverPhone;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String addressDetail;

    @NotNull(message = "Tỉnh/Thành phố không được để trống")
    private Long provinceId;

    @NotNull(message = "Quận/Huyện không được để trống")
    private Long districtId;
    /**
     * GHN WardCode (string, ví dụ: "550113") — KHÔNG phải WardID (số nguyên).
     * FE lấy từ API GHN /master-data/ward, dùng trường WardCode, không dùng WardID.
     */
    @NotBlank(message = "Phường/Xã không được để trống")
    private String wardCode;

    private Boolean isDefault;
}
