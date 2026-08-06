package com.zone.agri.exception;

import com.zone.agri.utils.MessagesUtils;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ConflictException extends RuntimeException {

    private String message;
    private String code;
    private Map<String, Object> payload;

    public ConflictException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    // Constructor mới - nhận message trực tiếp mà không cần MessagesUtils
    public ConflictException(String message, boolean isDirectMessage) {
        this.message = isDirectMessage ? message : MessagesUtils.getMessage(message);
    }

    public ConflictException(String code, String message, Map<String, Object> payload) {
        this.code = code;
        this.message = message;
        this.payload = payload;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
