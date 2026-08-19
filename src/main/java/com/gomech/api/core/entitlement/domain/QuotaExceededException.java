package com.gomech.api.core.entitlement.domain;

/**
 * Lançada quando uma operação excede os limites de cota da assinatura ativa do Tenant.
 */
public class QuotaExceededException extends RuntimeException {

    private final QuotaDimension dimension;
    private final long currentUsage;
    private final long limit;

    public QuotaExceededException(QuotaDimension dimension, long currentUsage, long limit, String message) {
        super(message);
        this.dimension = dimension;
        this.currentUsage = currentUsage;
        this.limit = limit;
    }

    public QuotaDimension getDimension() {
        return dimension;
    }

    public long getCurrentUsage() {
        return currentUsage;
    }

    public long getLimit() {
        return limit;
    }
}
