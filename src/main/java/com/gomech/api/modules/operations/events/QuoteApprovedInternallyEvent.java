package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteApprovedInternallyEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        UUID approverUserId,
        BigDecimal totalAmount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteApprovedInternallyEvent(UUID quoteId, UUID tenantId, UUID unitId, UUID approverUserId, BigDecimal totalAmount) {
        this(quoteId, tenantId, unitId, approverUserId, totalAmount, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.approved_internally";
    }
}
