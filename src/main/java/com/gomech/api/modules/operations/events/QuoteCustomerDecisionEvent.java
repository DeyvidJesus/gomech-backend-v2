package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteCustomerDecisionEvent(
        UUID quoteId,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        CustomerApprovalStatus decision,
        BigDecimal totalAmount,
        String notes,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public QuoteCustomerDecisionEvent(UUID quoteId, UUID tenantId, UUID unitId, UUID customerId, CustomerApprovalStatus decision, BigDecimal totalAmount, String notes) {
        this(quoteId, tenantId, unitId, customerId, decision, totalAmount, notes, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.quote.customer_decision";
    }
}
