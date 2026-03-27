package com.zone.agri.dto.response.admin;

import com.zone.agri.entity.enums.BranchStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BranchDTO {
    private Long id;

    @NotBlank(message = "Mã chi nhánh không được để trống")
    private String branchCode;

    private String branchType;

    @NotBlank(message = "Tên chi nhánh không được để trống")
    private String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải là 10 chữ số")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String addressDetail;

    private Integer provinceId;
    private Integer districtId;
    private Integer wardId;
    /** GHN WardCode (string, ví dụ: "550113") — dùng cho shipping fee API */
    private String wardCode;
    private String provinceName;
    private String districtName;
    private String wardName;
    /** Tọa độ — tự động geocode từ addressDetail khi create/update */
    private Double lat;
    private Double lng;
    private List<Long> managerIds;
    private List<String> managerNames;
    private BranchStatus status;
}
