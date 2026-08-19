package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.operations.domain.WorkOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderStatusChangedEvent(
        UUID workOrderId,
        UUID tenantId,
        UUID unitId,
        WorkOrderStatus previousStatus,
        WorkOrderStatus newStatus,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public WorkOrderStatusChangedEvent(UUID workOrderId, UUID tenantId, UUID unitId, WorkOrderStatus previousStatus, WorkOrderStatus newStatus) {
        this(workOrderId, tenantId, unitId, previousStatus, newStatus, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "operations.work_order.status_changed";
    }
}
