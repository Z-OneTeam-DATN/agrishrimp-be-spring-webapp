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
    private String provinceName;
    private String districtName;
    private  String wardName;
    private List<Long> managerIds;
    private List<String> managerNames;
    private BranchStatus status;
}