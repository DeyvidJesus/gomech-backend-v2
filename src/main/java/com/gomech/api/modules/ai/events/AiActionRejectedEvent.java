package com.gomech.api.modules.ai.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.ai.domain.AiActionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiActionRejectedEvent(
        UUID proposalId,
        UUID tenantId,
        UUID unitId,
        UUID rejectedByUserId,
        AiActionType actionType,
        String rejectionReason,
        OffsetDateTime rejectedAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "ai.action.rejected";
    }
}
