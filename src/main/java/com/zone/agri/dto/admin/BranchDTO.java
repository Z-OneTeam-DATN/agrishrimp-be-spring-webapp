package com.zone.agri.dto.admin;

import com.zone.agri.entity.enums.BranchStatus;
import lombok.Data;

import java.util.List;

@Data
public class BranchDTO {
    private Long id;
    private String branchCode;
    private String branchType;
    private String name;
    private String phone;
    private String email;
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