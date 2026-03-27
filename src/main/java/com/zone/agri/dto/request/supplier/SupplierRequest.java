package com.zone.agri.dto.request.supplier;

import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.validation.constraints.Email;
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

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|84)[3|5|7|8|9][0-9]{8}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã tỉnh/thành không được để trống")
    private String provinceId;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String addressDetail;

    @NotNull(message = "Trạng thái không được để trống")
    private SupplierStatus status;
}
