package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerDeletedEvent(
        UUID tenantId,
        UUID customerId,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public CustomerDeletedEvent(UUID tenantId, UUID customerId) {
        this(tenantId, customerId, OffsetDateTime.now());
    }
}
