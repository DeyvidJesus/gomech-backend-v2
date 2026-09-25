package com.gomech.api.modules.billing.events;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionStatusChangedEvent(
        UUID tenantId,
        UUID subscriptionId,
        String planCode,
        SubscriptionStatus previousStatus,
        SubscriptionStatus newStatus,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public SubscriptionStatusChangedEvent(UUID tenantId, UUID subscriptionId, String planCode, SubscriptionStatus previousStatus, SubscriptionStatus newStatus) {
        this(tenantId, subscriptionId, planCode, previousStatus, newStatus, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "billing.subscription.status_changed";
    }
}
