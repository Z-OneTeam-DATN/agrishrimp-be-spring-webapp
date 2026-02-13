package com.zone.agri.entity.enums;

public enum InventoryTransferStatus {
    PENDING,    // Chờ xử lý
    SHIPPING,   // Đang vận chuyển
    COMPLETED,  // Đã hoàn thành (bên kia đã nhận)
    CANCELLED   // Đã hủy
}
