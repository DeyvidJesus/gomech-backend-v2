package com.gomech.api.core.authorization.api;

public record AccessDecision(
    boolean allowed,
    String reason
) {

    public static AccessDecision allow(String reason) {
        return new AccessDecision(true, reason);
    }

    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
