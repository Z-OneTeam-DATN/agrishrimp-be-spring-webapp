package com.zone.agri.dto.request.supplier;

import com.zone.agri.entity.enums.SupplierStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    private String name;

    @NotBlank(message = "Mã số thuế không được để trống")
    private String taxCode;

    @NotBlank(message = "Tên người liên hệ không được để trống")
    private String contactName;

    @Pattern(regexp = "^$|^(0|84)[3|5|7|8|9][0-9]{8}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Pattern(regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã tỉnh/thành không được để trống")
    private String provinceId;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String addressDetail;

    @NotNull(message = "Trạng thái không được để trống")
    private SupplierStatus status;

    @AssertTrue(message = "Cần nhập ít nhất Số điện thoại hoặc Email")
    public boolean isAtLeastOneContactProvided() {
        return hasText(phone) || hasText(email);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
