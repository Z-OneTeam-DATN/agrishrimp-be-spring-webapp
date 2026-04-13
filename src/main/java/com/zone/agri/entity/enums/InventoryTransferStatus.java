package com.zone.agri.entity.enums;

public enum InventoryTransferStatus {
    PENDING,    // Chờ duyệt (Chi nhánh tạo)
    APPROVED,   // Đã duyệt (Kho tổng duyệt)
    SHIPPING,   // Đang vận chuyển (Kho tổng xuất đi)
    COMPLETED,  // Đã hoàn thành (Bên kia đã nhận và kiểm đếm)
    REJECTED,   // Từ chối
    CANCELLED   // Đã hủy
}
