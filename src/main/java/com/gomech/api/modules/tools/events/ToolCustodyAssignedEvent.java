package com.gomech.api.modules.tools.events;

import com.gomech.api.core.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ToolCustodyAssignedEvent(
        UUID tenantId,
        UUID unitId,
        UUID toolId,
        String toolName,
        UUID userId,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public ToolCustodyAssignedEvent(UUID tenantId, UUID unitId, UUID toolId, String toolName, UUID userId) {
        this(tenantId, unitId, toolId, toolName, userId, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "tools.custody.assigned";
    }
}
