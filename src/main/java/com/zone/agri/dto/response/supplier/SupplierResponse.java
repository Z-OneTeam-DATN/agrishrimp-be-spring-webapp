package com.zone.agri.dto.response.supplier;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {

    private Long id;
    private String code;
    private String name;
    private String taxCode;

    // Thông tin liên hệ chính
    private String contactName;
    private String phone;
    private String email;

    // Địa chỉ
    private String addressDetail;
    private String provinceId;

    // Trạng thái
    private SupplierStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Hàm tiện ích Map từ Entity sang Response ---
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
                .build();
    }
}
