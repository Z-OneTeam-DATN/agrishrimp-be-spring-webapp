package com.zone.agri.dto.branch;

import com.zone.agri.entity.enums.BranchStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BranchRequest {

    @NotBlank(message = "Mã chi nhánh không được để trống")
    String branchCode;

    @NotBlank(message = "Tên chi nhánh không được để trống")
    String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải là 10 chữ số")
    String phone;

    @Email(message = "Email không hợp lệ")
    String email;

    String addressDetail;

    Integer provinceId;

    Integer districtId;

    BranchStatus status; // ACTIVE, INACTIVE
}