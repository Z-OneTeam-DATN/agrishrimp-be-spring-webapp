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
    private Long productVariantId;
    private String sku;
    private Long productId;
    private String productName;
    private String productSlug;
    private String imageUrl;
    private String brandName;
    private String origin;
    private String categoryName;
    private SupplierProductCatalogStatus status;
    private String note;
    private LocalDateTime statusChangedAt;
    private Integer version;
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
                .productVariantId(catalog.getProductVariant() != null ? catalog.getProductVariant().getId() : null)
                .sku(catalog.getProductVariant() != null ? catalog.getProductVariant().getSku() : null)
                .productId(catalog.getProductVariant() != null && catalog.getProductVariant().getProduct() != null
                        ? catalog.getProductVariant().getProduct().getId()
                        : null)
                .productName(catalog.getProductVariant() != null && catalog.getProductVariant().getProduct() != null
                        ? catalog.getProductVariant().getProduct().getName()
                        : null)
                .productSlug(catalog.getProductVariant() != null && catalog.getProductVariant().getProduct() != null
                        ? catalog.getProductVariant().getProduct().getSlug()
                        : null)
                .imageUrl(catalog.getProductVariant() != null && catalog.getProductVariant().getImageUrl() != null
                        ? catalog.getProductVariant().getImageUrl()
                        : catalog.getProductVariant() != null
                                && catalog.getProductVariant().getProduct() != null
                                && catalog.getProductVariant().getProduct().getProductImages() != null
                                ? catalog.getProductVariant().getProduct().getProductImages().stream()
                                        .map(productImage -> productImage.getImageUrl())
                                        .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                                        .findFirst()
                                        .orElse(null)
                                : null)
                .brandName(catalog.getProductVariant() != null && catalog.getProductVariant().getProduct() != null
                        && catalog.getProductVariant().getProduct().getBrand() != null
                        ? catalog.getProductVariant().getProduct().getBrand().getName()
                        : null)
                .origin(null)
                .categoryName(catalog.getProductVariant() != null && catalog.getProductVariant().getProduct() != null
                        && catalog.getProductVariant().getProduct().getCategory() != null
                        ? catalog.getProductVariant().getProduct().getCategory().getName()
                        : null)
                .status(catalog.getStatus())
                .note(catalog.getNote())
                .statusChangedAt(catalog.getStatusChangedAt())
                .version(catalog.getVersion())
                .createdAt(catalog.getCreatedAt())
                .updatedAt(catalog.getUpdatedAt())
                .createdByUserId(catalog.getCreatedByUserId())
                .updatedByUserId(catalog.getUpdatedByUserId())
                .build();
    }
}
