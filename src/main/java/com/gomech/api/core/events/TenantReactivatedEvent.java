package com.gomech.api.core.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantReactivatedEvent(
        UUID tenantId,
        String reason,
        OffsetDateTime reactivatedAt
) {}
