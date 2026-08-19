package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteSubmittedForApprovalEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        BigDecimal totalAmount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteSubmittedForApprovalEvent(UUID quoteId, UUID tenantId, UUID unitId, BigDecimal totalAmount) {
        this(quoteId, tenantId, unitId, totalAmount, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.submitted_for_approval";
    }
}
