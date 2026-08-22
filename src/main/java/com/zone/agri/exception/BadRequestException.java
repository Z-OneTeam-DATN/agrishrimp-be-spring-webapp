package com.zone.agri.exception;

import com.zone.agri.utils.MessagesUtils;
import java.util.Map;
import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {

    private final String message;
    private final String code;
    private final Map<String, Object> payload;

    public BadRequestException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
        this.code = null;
        this.payload = null;
    }

    public BadRequestException(String code, String message, Map<String, Object> payload) {
        this.message = message;
        this.code = code;
        this.payload = payload;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
