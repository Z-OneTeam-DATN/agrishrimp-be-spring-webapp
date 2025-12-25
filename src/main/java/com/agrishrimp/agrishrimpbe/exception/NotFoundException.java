package com.agrishrimp.agrishrimpbe.exception;

import com.agrishrimp.agrishrimpbe.utils.MessagesUtils;

public class NotFoundException extends RuntimeException {

    private String message;

    public NotFoundException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    public NotFoundException() {
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}


