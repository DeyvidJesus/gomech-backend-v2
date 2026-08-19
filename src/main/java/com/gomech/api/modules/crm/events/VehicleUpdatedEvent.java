package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleUpdatedEvent(
        UUID tenantId,
        UUID vehicleId,
        String licensePlate,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public VehicleUpdatedEvent(UUID tenantId, UUID vehicleId, String licensePlate) {
        this(tenantId, vehicleId, licensePlate, OffsetDateTime.now());
    }
}
