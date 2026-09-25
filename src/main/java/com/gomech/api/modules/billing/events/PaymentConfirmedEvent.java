package com.gomech.api.modules.billing.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.billing.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentConfirmedEvent(
        UUID tenantId,
        UUID subscriptionId,
        UUID paymentId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String gatewayOrderId,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public PaymentConfirmedEvent(UUID tenantId, UUID subscriptionId, UUID paymentId, BigDecimal amount, PaymentMethod paymentMethod, String gatewayOrderId) {
        this(tenantId, subscriptionId, paymentId, amount, paymentMethod, gatewayOrderId, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "billing.payment.confirmed";
    }
}
