package com.zone.agri.dto.response.supplier;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {

    private Long id;
    private String code;
    private String name;
    private String taxCode;
    private String contactName;
    private String phone;
    private String email;
    private String addressDetail;
    private String provinceId;
    private SupplierStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdByUserId;
    private Long updatedByUserId;
    private String createdByName;
    private String updatedByName;
    private Integer catalogProductCount;
    private Integer availableProductCount;
    private Integer unavailableProductCount;
    private Integer checkingProductCount;
    private List<SupplierWarningResponse> warnings;

    public static SupplierResponse fromEntity(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .taxCode(supplier.getTaxCode())
                .contactName(supplier.getContactName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .addressDetail(supplier.getAddressDetail())
                .provinceId(supplier.getProvinceId())
                .status(supplier.getStatus())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .createdByUserId(supplier.getCreatedByUserId())
                .updatedByUserId(supplier.getUpdatedByUserId())
                .build();
    }
}
