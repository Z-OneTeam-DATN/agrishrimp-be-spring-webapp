package com.zone.agri.exception;

import com.zone.agri.utils.MessagesUtils;

public class NotFoundException extends RuntimeException {

    private String message;

    public NotFoundException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    public NotFoundException() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}


