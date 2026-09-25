package com.gomech.api.modules.ai.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.ai.domain.AiActionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiActionProposedEvent(
        UUID proposalId,
        UUID tenantId,
        UUID unitId,
        UUID actorUserId,
        AiActionType actionType,
        String targetResourceType,
        UUID targetResourceId,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "ai.action.proposed";
    }
}
