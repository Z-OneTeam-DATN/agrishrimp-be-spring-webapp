package com.zone.agri.dto.request.supplier;

import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SupplierProductCatalogRequest {
    @NotNull(message = "productVariantId không được để trống")
    private Long productVariantId;

    private SupplierProductCatalogStatus status;

    private java.math.BigDecimal price;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    private String note;

    private Integer version;

    private Boolean isDeleted;

    public Long getProductVariantId() {
        return productVariantId;
    }

    public void setProductVariantId(Long productVariantId) {
        this.productVariantId = productVariantId;
    }

    public SupplierProductCatalogStatus getStatus() {
        return status;
    }

    public void setStatus(SupplierProductCatalogStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public void setPrice(java.math.BigDecimal price) {
        this.price = price;
    }
}
