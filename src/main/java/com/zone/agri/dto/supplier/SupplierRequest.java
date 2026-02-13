package com.zone.agri.dto.supplier;

import com.zone.agri.entity.enums.PaymentTerm;
import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierRequest {
    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    private String name;

    @NotBlank(message = "Mã số thuế không được để trống")
    private String taxCode;

    private String category;

    @NotBlank(message = "Tên người liên hệ không được để trống")
    private String contactName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    private String email;
    private String provinceId;
    private String addressDetail;

    private PaymentTerm paymentTerms; // Map với field 'paymentTerms' ở FE
    private BigDecimal creditLimit;
    private Double discount;

    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolder;

    private SupplierStatus status;
    private String note;
}