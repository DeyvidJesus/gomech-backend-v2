package com.gomech.api.core.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantSuspendedEvent(
        UUID tenantId,
        String reason,
        OffsetDateTime suspendedAt
) {}
