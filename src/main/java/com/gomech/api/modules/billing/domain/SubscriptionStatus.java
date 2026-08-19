package com.gomech.api.modules.billing.domain;

/**
 * Estados do ciclo de vida de uma assinatura de Tenant.
 */
public enum SubscriptionStatus {
    TRIALING("Período de avaliação gratuita"),
    ACTIVE("Assinatura ativa e adimplente"),
    PAST_DUE("Pagamento pendente com carência"),
    CANCELED("Assinatura cancelada"),
    INCOMPLETE("Assinatura aguardando configuração inicial");

    private final String description;

    SubscriptionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isOperational(String status) {
        if (status == null) return false;
        return status.equalsIgnoreCase(TRIALING.name()) || status.equalsIgnoreCase(ACTIVE.name());
    }
}
