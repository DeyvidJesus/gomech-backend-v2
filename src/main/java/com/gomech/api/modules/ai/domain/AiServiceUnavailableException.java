package com.gomech.api.modules.ai.domain;

public class AiServiceUnavailableException extends AiException {

    public AiServiceUnavailableException(String message) {
        super(message, "AI_SERVICE_UNAVAILABLE");
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause, "AI_SERVICE_UNAVAILABLE");
    }
}
