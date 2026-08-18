package com.gomech.api.core.events;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID tenantId,
    UUID userId,
    String correlationId
) {
}
