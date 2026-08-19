package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerUpdatedEvent(
        UUID tenantId,
        UUID customerId,
        String name,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public CustomerUpdatedEvent(UUID tenantId, UUID customerId, String name) {
        this(tenantId, customerId, name, OffsetDateTime.now());
    }
}
