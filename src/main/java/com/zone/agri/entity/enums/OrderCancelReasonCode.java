package com.zone.agri.entity.enums;

import java.util.Locale;
import java.util.Optional;

public enum OrderCancelReasonCode {
    CHANGE_PRODUCT("Muốn thay đổi sản phẩm"),
    CHANGE_ADDRESS("Muốn thay đổi địa chỉ nhận hàng"),
    FOUND_CHEAPER("Tìm thấy giá rẻ hơn"),
    OTHER("Lý do khác"),
    PAYMENT_EXPIRED("Quá hạn thanh toán"),
    ADMIN_CANCELLED("Quản trị viên hủy đơn"),
    SUB_ORDERS_CANCELLED("Tất cả phần đơn đã bị hủy");

    private final String displayName;

    OrderCancelReasonCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<OrderCancelReasonCode> from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String normalized = rawValue.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "CHANGE_PRODUCT" -> Optional.of(CHANGE_PRODUCT);
            case "CHANGE_ADDRESS" -> Optional.of(CHANGE_ADDRESS);
            case "FOUND_CHEAPER" -> Optional.of(FOUND_CHEAPER);
            case "OTHER" -> Optional.of(OTHER);
            case "PAYMENT_EXPIRED" -> Optional.of(PAYMENT_EXPIRED);
            case "ADMIN_CANCELLED" -> Optional.of(ADMIN_CANCELLED);
            case "SUB_ORDERS_CANCELLED" -> Optional.of(SUB_ORDERS_CANCELLED);
            default -> Optional.empty();
        };
    }
}
