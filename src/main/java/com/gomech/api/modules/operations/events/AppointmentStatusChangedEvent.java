package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.operations.domain.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentStatusChangedEvent(
        UUID tenantId,
        UUID unitId,
        UUID appointmentId,
        AppointmentStatus previousStatus,
        AppointmentStatus newStatus,
        OffsetDateTime occurredAt
) implements DomainEvent {

    public AppointmentStatusChangedEvent(
            UUID tenantId,
            UUID unitId,
            UUID appointmentId,
            AppointmentStatus previousStatus,
            AppointmentStatus newStatus
    ) {
        this(tenantId, unitId, appointmentId, previousStatus, newStatus, OffsetDateTime.now());
    }
}
