package com.zone.agri.entity.enums;

public enum SupplierProductCatalogStatus {
    AVAILABLE("CÓ CUNG CẤP"),
    UNAVAILABLE("KHÔNG CUNG CẤP"),
    CHECKING("ĐANG KIỂM TRA");

    private final String displayName;

    SupplierProductCatalogStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
