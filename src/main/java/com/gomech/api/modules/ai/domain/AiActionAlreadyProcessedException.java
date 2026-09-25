package com.gomech.api.modules.ai.domain;

public class AiActionAlreadyProcessedException extends AiException {
    public AiActionAlreadyProcessedException(String message) {
        super(message, "AI_ACTION_ALREADY_PROCESSED");
    }
}
