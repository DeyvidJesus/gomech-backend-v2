package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderCanceledEvent(
        UUID workOrderId,
        UUID tenantId,
        UUID unitId,
        String reason,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public WorkOrderCanceledEvent(UUID workOrderId, UUID tenantId, UUID unitId, String reason) {
        this(workOrderId, tenantId, unitId, reason, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.work_order.canceled";
    }
}
