package com.gomech.api.modules.finance.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceivableCreatedEvent(
        UUID receivableId,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        UUID workOrderId,
        String orderNumber,
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        String status,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public ReceivableCreatedEvent(
            UUID receivableId,
            UUID tenantId,
            UUID unitId,
            UUID customerId,
            UUID workOrderId,
            String orderNumber,
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            String status
    ) {
        this(receivableId, tenantId, unitId, customerId, workOrderId, orderNumber, description, amount, dueDate, status, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "finance.receivable.created";
    }
}
