package com.gomech.api.modules.billing.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID tenantId,
        UUID subscriptionId,
        UUID paymentId,
        BigDecimal amount,
        String failureReason,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public PaymentFailedEvent(UUID tenantId, UUID subscriptionId, UUID paymentId, BigDecimal amount, String failureReason) {
        this(tenantId, subscriptionId, paymentId, amount, failureReason, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "billing.payment.failed";
    }
}
