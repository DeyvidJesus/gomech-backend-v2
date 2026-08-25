package com.gomech.api.modules.billing.infrastructure.gateway;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PagarmeDto {

    @Builder
    public record CustomerRequest(
            String name,
            String email,
            String document,
            String documentType, // CPF or CNPJ
            String type, // individual or company
            String phone
    ) {}

    @Builder
    public record CustomerResponse(
            String id,
            String name,
            String email,
            String document,
            String type
    ) {}

    @Builder
    public record CreateHostedCheckoutRequest(
            UUID tenantId,
            UUID subscriptionId,
            String pagarmePlanId,
            String customerId,
            String planCode,
            BigDecimal price,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata
    ) {}

    @Builder
    public record PaymentLinkResponse(
            String id,
            String url,
            String status,
            String type,
            String planId,
            OffsetDateTime createdAt
    ) {}

    @Builder
    public record PagarmePlanDto(
            String id,
            String name,
            String status,
            String interval,
            Integer intervalCount,
            String billingType,
            Integer priceInCents,
            BigDecimal price,
            List<String> paymentMethods
    ) {}

    @Builder
    public record WebhookEventPayload(
            String id,
            String type,
            String accountId,
            OffsetDateTime createdAt,
            WebhookData data
    ) {}

    @Builder
    public record WebhookData(
            String id,
            String code,
            BigDecimal amount,
            String status,
            String paymentMethod,
            String gatewayOrderId,
            String gatewayChargeId,
            String gatewaySubscriptionId,
            String customerId,
            String paymentLinkId,
            Map<String, String> metadata,
            OffsetDateTime paidAt
    ) {}
}
