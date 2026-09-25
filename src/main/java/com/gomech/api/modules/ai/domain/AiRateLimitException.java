package com.gomech.api.modules.ai.domain;

public class AiRateLimitException extends AiException {

    private final long retryAfterSeconds;

    public AiRateLimitException(String message, long retryAfterSeconds) {
        super(message, "AI_RATE_LIMIT_EXCEEDED");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
