package com.zone.agri.dto.request.supplier;

import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SupplierProductCatalogRequest {
    @NotNull(message = "productId không được để trống")
    private Long productId;

    @NotNull(message = "Trạng thái không được để trống")
    private SupplierProductCatalogStatus status;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    private String note;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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
}
