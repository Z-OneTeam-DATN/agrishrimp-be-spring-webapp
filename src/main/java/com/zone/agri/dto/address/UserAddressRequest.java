package com.zone.agri.dto.address;

import lombok.Data;

@Data
public class UserAddressRequest {
    private String receiverName;
    private String receiverPhone;
    private String addressDetail;
    private Long provinceId;
    private Long districtId;
    private Long wardId;
    private Boolean isDefault;
}