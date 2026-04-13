package com.zone.agri.entity.enums;

public enum InventoryNoteStatus {
    PENDING,    // Chờ duyệt (Manager tạo)
    APPROVED,   // Đã duyệt (Admin duyệt)
    COMPLETED,  // Đã nhập kho (Hàng đã về, đã kiểm đếm)
    REJECTED,   // Từ chối
    CANCELLED   // Đã hủy
}