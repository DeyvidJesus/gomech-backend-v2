package com.gomech.api.modules.ai.domain;

public class AiException extends RuntimeException {

    private final String errorCode;

    public AiException(String message) {
        super(message);
        this.errorCode = "AI_GATEWAY_ERROR";
    }

    public AiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
