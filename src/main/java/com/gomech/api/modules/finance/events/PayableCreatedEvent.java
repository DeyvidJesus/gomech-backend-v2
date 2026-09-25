package com.gomech.api.modules.finance.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PayableCreatedEvent(
        UUID payableId,
        UUID tenantId,
        UUID unitId,
        String supplierName,
        UUID inventoryPurchaseId,
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        String status,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public PayableCreatedEvent(
            UUID payableId,
            UUID tenantId,
            UUID unitId,
            String supplierName,
            UUID inventoryPurchaseId,
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            String status
    ) {
        this(payableId, tenantId, unitId, supplierName, inventoryPurchaseId, description, amount, dueDate, status, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "finance.payable.created";
    }
}
