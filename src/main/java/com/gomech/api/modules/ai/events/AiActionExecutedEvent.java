package com.gomech.api.modules.ai.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.ai.domain.AiActionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiActionExecutedEvent(
        UUID proposalId,
        UUID tenantId,
        UUID unitId,
        UUID confirmedByUserId,
        AiActionType actionType,
        String targetResourceType,
        UUID targetResourceId,
        OffsetDateTime executedAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "ai.action.executed";
    }
}
