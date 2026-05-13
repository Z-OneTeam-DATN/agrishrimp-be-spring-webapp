package com.zone.agri.dto.response.supplier;

import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProductCatalogResponse {
    private Long id;
    private Long supplierId;
    private String supplierCode;
    private Long productId;
    private String productName;
    private String productSlug;
    private String brandName;
    private String origin;
    private String categoryName;
    private SupplierProductCatalogStatus status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdByUserId;
    private Long updatedByUserId;
    private String createdByName;
    private String updatedByName;
    private Long checkingAgeDays;
    private Boolean checkingTooLong;

    public static SupplierProductCatalogResponse fromEntity(SupplierProductCatalog catalog) {
        return SupplierProductCatalogResponse.builder()
                .id(catalog.getId())
                .supplierId(catalog.getSupplier() != null ? catalog.getSupplier().getId() : null)
                .supplierCode(catalog.getSupplier() != null ? catalog.getSupplier().getCode() : null)
                .productId(catalog.getProduct() != null ? catalog.getProduct().getId() : null)
                .productName(catalog.getProduct() != null ? catalog.getProduct().getName() : null)
                .productSlug(catalog.getProduct() != null ? catalog.getProduct().getSlug() : null)
                .brandName(catalog.getProduct() != null && catalog.getProduct().getBrand() != null
                        ? catalog.getProduct().getBrand().getName()
                        : null)
                .origin(catalog.getProduct() != null ? catalog.getProduct().getOrigin() : null)
                .categoryName(catalog.getProduct() != null && catalog.getProduct().getCategory() != null
                        ? catalog.getProduct().getCategory().getName()
                        : null)
                .status(catalog.getStatus())
                .note(catalog.getNote())
                .createdAt(catalog.getCreatedAt())
                .updatedAt(catalog.getUpdatedAt())
                .createdByUserId(catalog.getCreatedByUserId())
                .updatedByUserId(catalog.getUpdatedByUserId())
                .build();
    }
}
