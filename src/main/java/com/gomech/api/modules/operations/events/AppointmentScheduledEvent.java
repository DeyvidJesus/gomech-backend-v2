package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentScheduledEvent(
        UUID tenantId,
        UUID unitId,
        UUID appointmentId,
        UUID customerId,
        UUID vehicleId,
        OffsetDateTime scheduledAt,
        String serviceType,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public AppointmentScheduledEvent(
            UUID tenantId,
            UUID unitId,
            UUID appointmentId,
            UUID customerId,
            UUID vehicleId,
            OffsetDateTime scheduledAt,
            String serviceType
    ) {
        this(tenantId, unitId, appointmentId, customerId, vehicleId, scheduledAt, serviceType, OffsetDateTime.now());
    }
}
