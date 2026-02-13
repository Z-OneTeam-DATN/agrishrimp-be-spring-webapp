package com.zone.agri.dto.customer;

import com.zone.agri.entity.enums.CustomerGender;
import com.zone.agri.entity.enums.CustomerStatus;
import lombok.Data;

@Data
public class CustomerRequest {
    private String name;
    private String phone;
    private String email;
    private CustomerGender gender;

    private String provinceId;
    private String districtId;
    private String wardId;
    private String addressDetail;

    private CustomerStatus status;
    private String note;
}