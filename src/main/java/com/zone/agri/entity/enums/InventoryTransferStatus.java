package com.zone.agri.entity.enums;

public enum InventoryTransferStatus {
    // ── Flow chung ──────────────────────────────────────────────────────────
    PENDING,            // Mới tạo / Chờ xác nhận (cả hai luồng)
    // ── Flow 5: Chi nhánh A → Chi nhánh B ──────────────────────────────────
    SOURCE_CONFIRMED,   // Chi nhánh A đã xác nhận có hàng & Reserve kho (Flow 5)
    // ── Flow 4 & 5 từ Admin duyệt trở đi ───────────────────────────────────
    APPROVED,           // Admin đã duyệt – hàng đang chờ xuất (Reserve đã thực hiện)
    SHIPPING,           // Hàng đã xuất khỏi kho nguồn – đang trên đường
    INSPECTING,         // Bên nhận đang kiểm đếm hàng
    COMPLETED,          // Đã hoàn thành – tồn kho hai bên đã cập nhật
    REJECTED,           // Bị từ chối
    CANCELLED           // Đã hủy
}
