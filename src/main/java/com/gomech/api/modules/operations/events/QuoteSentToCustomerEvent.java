package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteSentToCustomerEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        BigDecimal totalAmount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteSentToCustomerEvent(UUID quoteId, UUID tenantId, UUID unitId, UUID customerId, BigDecimal totalAmount) {
        this(quoteId, tenantId, unitId, customerId, totalAmount, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.sent_to_customer";
    }
}
