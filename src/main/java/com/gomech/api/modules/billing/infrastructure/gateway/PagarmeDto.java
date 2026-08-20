package com.gomech.api.modules.billing.infrastructure.gateway;

import com.gomech.api.modules.billing.domain.PaymentMethod;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PagarmeDto {

    @Builder
    public record CustomerRequest(
            String name,
            String email,
            String document,
            String phone
    ) {}

    @Builder
    public record CardDetails(
            String number,
            String holderName,
            int expMonth,
            int expYear,
            String cvv
    ) {}

    @Builder
    public record CreatePaymentRequest(
            UUID tenantId,
            UUID subscriptionId,
            BigDecimal amount,
            PaymentMethod method,
            String customerName,
            String customerEmail,
            String customerDocument,
            String customerPhone,
            CardDetails cardDetails,
            String cardToken,
            Integer installments,
            LocalDate boletoDueDate
    ) {}

    @Builder
    public record GatewayPaymentResult(
            String gatewayOrderId,
            String gatewayChargeId,
            String gatewayPaymentId,
            String status, // paid, pending, failed, canceled
            String paymentMethod,
            BigDecimal amount,
            String pixQrCode,
            String pixQrCodeUrl,
            String pixCopyPaste,
            OffsetDateTime pixExpiresAt,
            String boletoBarcode,
            String boletoUrl,
            LocalDate boletoDueDate,
            String cardLastFour,
            String cardBrand,
            String rawResponse
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
            OffsetDateTime paidAt
    ) {}
}
