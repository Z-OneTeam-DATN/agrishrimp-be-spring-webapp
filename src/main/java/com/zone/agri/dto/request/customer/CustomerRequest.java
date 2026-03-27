package com.zone.agri.dto.request.customer;

import com.zone.agri.entity.enums.CustomerGender;
import com.zone.agri.entity.enums.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerRequest {
    @NotBlank(message = "Tên không được để trống")
    private String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)(\\s|\\.)?((3[2-9])|(5[689])|(7[06-9])|(8[1-689])|(9[0-46-9]))(\\d)(\\s|\\.)?(\\d{3})(\\s|\\.)?(\\d{3})$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    @NotNull(message = "Giới tính không được để trống")
    private CustomerGender gender;

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    private String provinceId;

    @NotBlank(message = "Quận/Huyện không được để trống")
    private String districtId;

    @NotBlank(message = "Phường/Xã không được để trống")
    private String wardId;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String addressDetail;

    private CustomerStatus status;
    private String note;
}
