package com.zone.agri.entity.enums;

/**
 * Trạng thái thanh toán công nợ nội bộ (chỉ áp dụng khi transferBusinessType = INTERNAL_SALE):
 * - UNPAID:   Chưa thanh toán - kho nhận chưa trả tiền kho xuất
 * - PARTIAL:  Thanh toán một phần
 * - PAID:     Đã thanh toán đủ
 */
public enum TransferSettlementStatus {
    UNPAID,
    PARTIAL,
    PAID
}
