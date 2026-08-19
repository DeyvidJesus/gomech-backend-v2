package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionCreatedEvent(
        UUID tenantId,
        UUID unitId,
        UUID inspectionId,
        UUID vehicleId,
        UUID customerId,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public InspectionCreatedEvent(
            UUID tenantId,
            UUID unitId,
            UUID inspectionId,
            UUID vehicleId,
            UUID customerId
    ) {
        this(tenantId, unitId, inspectionId, vehicleId, customerId, OffsetDateTime.now());
    }
}
