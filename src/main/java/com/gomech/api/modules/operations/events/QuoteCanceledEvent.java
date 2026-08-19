package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteCanceledEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        String reason,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteCanceledEvent(UUID quoteId, UUID tenantId, UUID unitId, String reason) {
        this(quoteId, tenantId, unitId, reason, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.canceled";
    }
}
