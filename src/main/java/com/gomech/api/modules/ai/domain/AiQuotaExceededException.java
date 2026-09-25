package com.gomech.api.modules.ai.domain;

public class AiQuotaExceededException extends AiException {

    public AiQuotaExceededException(String message) {
        super(message, "AI_QUOTA_EXCEEDED");
    }
}
