package com.gomech.api.modules.crm.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerCreatedEvent(
        UUID tenantId,
        UUID customerId,
        String name,
        String document,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public CustomerCreatedEvent(UUID tenantId, UUID customerId, String name, String document) {
        this(tenantId, customerId, name, document, OffsetDateTime.now());
    }
}
