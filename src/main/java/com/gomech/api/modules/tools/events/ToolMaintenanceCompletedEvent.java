package com.gomech.api.modules.tools.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ToolMaintenanceCompletedEvent(
        UUID maintenanceId,
        UUID tenantId,
        UUID unitId,
        UUID toolId,
        String toolName,
        BigDecimal cost,
        BigDecimal downtimeHours,
        String provider,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public ToolMaintenanceCompletedEvent(
            UUID maintenanceId,
            UUID tenantId,
            UUID unitId,
            UUID toolId,
            String toolName,
            BigDecimal cost,
            BigDecimal downtimeHours,
            String provider
    ) {
        this(maintenanceId, tenantId, unitId, toolId, toolName, cost, downtimeHours, provider, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "tools.maintenance.completed";
    }
}
