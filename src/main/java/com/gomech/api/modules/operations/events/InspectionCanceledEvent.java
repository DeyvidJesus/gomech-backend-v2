package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionCanceledEvent(
        UUID tenantId,
        UUID unitId,
        UUID inspectionId,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public InspectionCanceledEvent(
            UUID tenantId,
            UUID unitId,
            UUID inspectionId
    ) {
        this(tenantId, unitId, inspectionId, OffsetDateTime.now());
    }
}
