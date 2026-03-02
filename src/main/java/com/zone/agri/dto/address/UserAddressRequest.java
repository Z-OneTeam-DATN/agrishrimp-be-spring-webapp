package com.zone.agri.dto.address;

import lombok.Data;

@Data
public class UserAddressRequest {
    private String receiverName;
    private String receiverPhone;
    private String addressDetail;
    private Long provinceId;
    private Long districtId;
    /**
     * GHN WardCode (string, ví dụ: "550113") — KHÔNG phải WardID (số nguyên).
     * FE lấy từ API GHN /master-data/ward, dùng trường WardCode, không dùng WardID.
     */
    private String wardCode;
    private Boolean isDefault;
}