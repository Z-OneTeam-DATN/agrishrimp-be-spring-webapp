package com.zone.agri.converter;

import com.zone.agri.entity.enums.UserStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        // Legacy compatibility: old BANNED accounts are treated as INACTIVE.
        if ("BANNED".equalsIgnoreCase(dbData)) {
            return UserStatus.INACTIVE;
        }

        return UserStatus.valueOf(dbData.trim().toUpperCase());
    }
}
