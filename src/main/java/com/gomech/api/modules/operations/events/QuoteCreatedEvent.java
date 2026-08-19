package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteCreatedEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        UUID vehicleId,
        UUID inspectionId,
        BigDecimal totalAmount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteCreatedEvent(UUID quoteId, UUID tenantId, UUID unitId, UUID customerId, UUID vehicleId, UUID inspectionId, BigDecimal totalAmount) {
        this(quoteId, tenantId, unitId, customerId, vehicleId, inspectionId, totalAmount, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.created";
    }
}
