package com.gomech.api.modules.ai.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.ai.domain.AiCapability;
import com.gomech.api.modules.ai.domain.AiGatewayStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiRequestAuditedEvent(
        UUID tenantId,
        UUID unitId,
        UUID userId,
        String correlationId,
        AiCapability capability,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        AiGatewayStatus status,
        String errorCode,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public AiRequestAuditedEvent(
            UUID tenantId,
            UUID unitId,
            UUID userId,
            String correlationId,
            AiCapability capability,
            String model,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            AiGatewayStatus status,
            String errorCode
    ) {
        this(tenantId, unitId, userId, correlationId, capability, model, promptTokens, completionTokens, totalTokens, latencyMs, status, errorCode, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "ai.request.audited";
    }
}
