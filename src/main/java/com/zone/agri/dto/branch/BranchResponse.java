package com.zone.agri.dto.branch;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BranchResponse {

    Long id;
    String branchCode;
    String name;
    String phone;
    String email;
    String addressDetail;
    Integer provinceId;
    Integer districtId;
    BranchStatus status;
    LocalDateTime createdAt;

    // Hàm static để convert nhanh từ Entity sang Response
    public static BranchResponse from(Branch branch) {
        if (branch == null) return null;
        return BranchResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .name(branch.getName())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .addressDetail(branch.getAddressDetail())
                .provinceId(branch.getProvinceId())
                .districtId(branch.getDistrictId())
                .status(branch.getStatus())
                .createdAt(branch.getCreatedAt())
                .build();
    }
}