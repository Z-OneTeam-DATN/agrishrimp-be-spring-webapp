package com.zone.agri.entity.enums;

public enum NoteStatus {
    PENDING,        // Chờ duyệt (Lệnh mới tạo)
    IN_PROGRESS,    // Đang xử lý (Đang lấy hàng)
    COMPLETED,      // Hoàn thành (Đã xuất)
    CANCELLED       // Đã hủy
}