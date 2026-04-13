package com.zone.agri.exception;

public class RateLimitException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.code = "ORDER_RATE_LIMITED";
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}