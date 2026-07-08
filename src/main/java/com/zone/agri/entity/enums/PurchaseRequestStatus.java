package com.zone.agri.entity.enums;

public enum PurchaseRequestStatus {
    DRAFT,              // Nháp - Mới tạo, chưa gửi duyệt
    PENDING_APPROVAL,   // Chờ duyệt
    APPROVED,           // Đã duyệt - Sẵn sàng gửi NCC
    SENT_TO_SUPPLIER,   // Đã gửi NCC - Đang chờ NCC xác nhận
    SUPPLIER_CONFIRMED, // NCC đã xác nhận
    PREPARING,          // NCC đang chuẩn bị hàng
    DELIVERING,         // NCC đang giao hàng
    PARTIALLY_RECEIVED, // Nhận một phần - Đã có ít nhất 1 phiếu nhập COMPLETED
    COMPLETED,          // Hoàn tất - acceptedQty lũy kế đủ với requestedQty
    CANCELLED,          // Đã hủy
    CLOSED              // Đóng thủ công (còn thiếu nhưng force close)
}
