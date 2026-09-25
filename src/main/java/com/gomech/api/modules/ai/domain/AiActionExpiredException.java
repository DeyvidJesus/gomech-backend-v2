package com.gomech.api.modules.ai.domain;

public class AiActionExpiredException extends AiException {
    public AiActionExpiredException(String message) {
        super(message, "AI_ACTION_EXPIRED");
    }
}
