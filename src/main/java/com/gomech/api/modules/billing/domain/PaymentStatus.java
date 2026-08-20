package com.gomech.api.modules.billing.domain;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
