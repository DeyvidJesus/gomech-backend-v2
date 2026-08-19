package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentCanceledEvent(
        UUID tenantId,
        UUID unitId,
        UUID appointmentId,
        String cancellationReason,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public AppointmentCanceledEvent(
            UUID tenantId,
            UUID unitId,
            UUID appointmentId,
            String cancellationReason
    ) {
        this(tenantId, unitId, appointmentId, cancellationReason, OffsetDateTime.now());
    }
}
