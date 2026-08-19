package com.gomech.api.core.entitlement.api;

/**
 * Resultado da avaliação de elegibilidade de acesso a recursos ou módulos.
 */
public record EntitlementDecision(
        boolean allowed,
        String reason,
        String featureCode
) {
    public static EntitlementDecision allow(String featureCode, String reason) {
        return new EntitlementDecision(true, reason, featureCode);
    }

    public static EntitlementDecision deny(String featureCode, String reason) {
        return new EntitlementDecision(false, reason, featureCode);
    }
}
