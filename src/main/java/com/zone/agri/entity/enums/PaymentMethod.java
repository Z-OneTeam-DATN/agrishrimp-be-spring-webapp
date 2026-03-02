package com.zone.agri.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    COD,
    PAYOS;

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null) return null;
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.name().equalsIgnoreCase(value)) {
                return method;
            }
        }
        return null;
    }
}
