package com.zone.agri.dto.supplier;

import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    private String name;

    @NotBlank(message = "Mã số thuế không được để trống")
    private String taxCode;

    @NotBlank(message = "Tên người liên hệ không được để trống")
    private String contactName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    private String email;
    private String provinceId;
    private String addressDetail;

    private SupplierStatus status;
}