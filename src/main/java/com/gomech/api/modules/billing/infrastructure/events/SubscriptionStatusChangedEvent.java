package com.gomech.api.modules.billing.infrastructure.events;

import com.gomech.api.modules.billing.domain.SubscriptionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionStatusChangedEvent(
        UUID tenantId,
        UUID subscriptionId,
        String planCode,
        SubscriptionStatus previousStatus,
        SubscriptionStatus newStatus,
        OffsetDateTime timestamp
) {}
