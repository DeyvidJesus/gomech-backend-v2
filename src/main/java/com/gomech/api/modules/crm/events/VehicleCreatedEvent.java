package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleCreatedEvent(
        UUID tenantId,
        UUID vehicleId,
        UUID customerId,
        String licensePlate,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public VehicleCreatedEvent(UUID tenantId, UUID vehicleId, UUID customerId, String licensePlate) {
        this(tenantId, vehicleId, customerId, licensePlate, OffsetDateTime.now());
    }
}
