package com.gomech.api.core.entitlement.api;

import com.gomech.api.core.entitlement.domain.QuotaDimension;

/**
 * Resultado da avaliação de limite de cota para uma dimensão específica.
 */
public record QuotaDecision(
        boolean allowed,
        long currentUsage,
        long limit,
        QuotaDimension dimension,
        String reason
) {
    public static QuotaDecision allow(QuotaDimension dimension, long currentUsage, long limit, String reason) {
        return new QuotaDecision(true, currentUsage, limit, dimension, reason);
    }

    public static QuotaDecision deny(QuotaDimension dimension, long currentUsage, long limit, String reason) {
        return new QuotaDecision(false, currentUsage, limit, dimension, reason);
    }
}
