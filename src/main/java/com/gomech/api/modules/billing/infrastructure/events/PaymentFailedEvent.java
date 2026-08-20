package com.gomech.api.modules.billing.infrastructure.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID tenantId,
        UUID subscriptionId,
        UUID paymentId,
        BigDecimal amount,
        String failureReason,
        OffsetDateTime failedAt
) {}
