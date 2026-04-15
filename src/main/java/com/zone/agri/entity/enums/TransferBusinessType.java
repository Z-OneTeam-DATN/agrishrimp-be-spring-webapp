package com.zone.agri.entity.enums;

/**
 * Loại nghiệp vụ điều chuyển:
 * - STOCK_TRANSFER: Điều chuyển kho thuần - cùng một đơn vị kinh doanh, không phát sinh doanh thu / công nợ nội bộ
 * - INTERNAL_SALE:  Bán nội bộ / Điều chuyển có hạch toán - 2 chi nhánh là 2 đơn vị kinh doanh riêng,
 *                   phát sinh giá bán nội bộ, doanh thu nội bộ cho bên xuất và công nợ nội bộ cho bên nhận
 */
public enum TransferBusinessType {
    STOCK_TRANSFER,
    INTERNAL_SALE
}
