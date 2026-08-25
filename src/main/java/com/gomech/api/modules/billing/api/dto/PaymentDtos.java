package com.gomech.api.modules.billing.api.dto;

import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentDtos {

    @Builder
    @Schema(description = "Requisição para criação de sessão de checkout hospedado Pagar.me")
    public record CreateCheckoutRequest(
            @NotBlank
            @Schema(description = "Código do plano a ser assinado", example = "PRO")
            String planCode,

            @Schema(description = "URL de retorno com sucesso")
            String successUrl,

            @Schema(description = "URL de retorno em caso de cancelamento")
            String cancelUrl
    ) {}

    @Builder
    @Schema(description = "Resposta com URL oficial do checkout hospedado Pagar.me")
    public record CheckoutSessionResponse(
            @Schema(description = "URL de redirecionamento para o checkout hospedado Pagar.me")
            String checkoutUrl,

            @Schema(description = "Código do plano")
            String planCode,

            @Schema(description = "Preço mensal do plano")
            BigDecimal price,

            @Schema(description = "ID do link de pagamento gerado")
            String paymentLinkId,

            @Schema(description = "ID da transação/ordem de pagamento local")
            UUID paymentId
    ) {}

    @Builder
    @Schema(description = "Dados detalhados do pagamento / fatura")
    public record PaymentResponse(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            BigDecimal amount,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            LocalDate dueDate,
            OffsetDateTime paidAt,
            String gatewayOrderId,
            String gatewayChargeId,
            String gatewayPaymentId,
            String gatewayPaymentLinkId,
            String pixQrCode,
            String pixQrCodeUrl,
            String pixCopyPaste,
            OffsetDateTime pixExpiresAt,
            String boletoBarcode,
            String boletoUrl,
            LocalDate boletoDueDate,
            Integer installments,
            String cardLastFour,
            String cardBrand,
            OffsetDateTime createdAt
    ) {}

    @Builder
    @Schema(description = "Requisição para cancelamento de assinatura")
    public record CancelSubscriptionRequest(
            String reason
    ) {}
}
