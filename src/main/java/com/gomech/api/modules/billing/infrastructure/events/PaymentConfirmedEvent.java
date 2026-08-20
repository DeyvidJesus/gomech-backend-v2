package com.gomech.api.modules.billing.infrastructure.events;

import com.gomech.api.modules.billing.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentConfirmedEvent(
        UUID tenantId,
        UUID subscriptionId,
        UUID paymentId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String gatewayOrderId,
        OffsetDateTime paidAt
) {}
