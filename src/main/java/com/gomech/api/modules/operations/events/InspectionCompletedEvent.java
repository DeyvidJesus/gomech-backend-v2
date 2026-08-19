package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionCompletedEvent(
        UUID tenantId,
        UUID unitId,
        UUID inspectionId,
        UUID vehicleId,
        int totalItems,
        int criticalItems,
        int attentionItems,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public InspectionCompletedEvent(
            UUID tenantId,
            UUID unitId,
            UUID inspectionId,
            UUID vehicleId,
            int totalItems,
            int criticalItems,
            int attentionItems
    ) {
        this(tenantId, unitId, inspectionId, vehicleId, totalItems, criticalItems, attentionItems, OffsetDateTime.now());
    }
}
