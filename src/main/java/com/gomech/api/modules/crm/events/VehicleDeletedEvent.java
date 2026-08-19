package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleDeletedEvent(
        UUID tenantId,
        UUID vehicleId,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public VehicleDeletedEvent(UUID tenantId, UUID vehicleId) {
        this(tenantId, vehicleId, OffsetDateTime.now());
    }
}
