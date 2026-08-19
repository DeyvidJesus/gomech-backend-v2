package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderCreatedEvent(
        UUID workOrderId,
        UUID tenantId,
        UUID unitId,
        String orderNumber,
        UUID customerId,
        UUID vehicleId,
        UUID quoteId,
        UUID mechanicUserId,
        BigDecimal totalAmount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public WorkOrderCreatedEvent(
            UUID workOrderId,
            UUID tenantId,
            UUID unitId,
            String orderNumber,
            UUID customerId,
            UUID vehicleId,
            UUID quoteId,
            UUID mechanicUserId,
            BigDecimal totalAmount
    ) {
        this(workOrderId, tenantId, unitId, orderNumber, customerId, vehicleId, quoteId, mechanicUserId, totalAmount, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.work_order.created";
    }
}
