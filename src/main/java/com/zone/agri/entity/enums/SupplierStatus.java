package com.zone.agri.entity.enums;

public enum SupplierStatus {
    ACTIVE("ĐANG GIAO DỊCH"),
    INACTIVE("TẠM DỪNG");

    private final String displayName;

    SupplierStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
