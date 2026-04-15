package com.zone.agri.exception;

import com.zone.agri.utils.MessagesUtils;
import lombok.Setter;

@Setter
public class ConflictException extends RuntimeException {

    private String message;

    public ConflictException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    // Constructor mới - nhận message trực tiếp mà không cần MessagesUtils
    public ConflictException(String message, boolean isDirectMessage) {
        this.message = isDirectMessage ? message : MessagesUtils.getMessage(message);
    }

    @Override
    public String getMessage() {
        return message;
    }

}