package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderAssignedEvent(
        UUID workOrderId,
        UUID tenantId,
        UUID unitId,
        UUID mechanicUserId,
        String serviceBay,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public WorkOrderAssignedEvent(UUID workOrderId, UUID tenantId, UUID unitId, UUID mechanicUserId, String serviceBay) {
        this(workOrderId, tenantId, unitId, mechanicUserId, serviceBay, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.work_order.assigned";
    }
}
