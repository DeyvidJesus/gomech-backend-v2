package com.gomech.api.modules.iam.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de domínio emitido pelo módulo IAM quando uma nova organização/tenant é cadastrada com sucesso.
 */
public record TenantCreatedEvent(
        UUID tenantId,
        String workshopName,
        String ownerEmail,
        OffsetDateTime occurredAt
) implements DomainEvent {
    public TenantCreatedEvent(UUID tenantId, String workshopName, String ownerEmail) {
        this(tenantId, workshopName, ownerEmail, OffsetDateTime.now());
    }
}
