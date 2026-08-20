package com.gomech.api.modules.billing.api.dto;

import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentDtos {

    @Builder
    @Schema(description = "Requisição para iniciar pagamento de plano/assinatura")
    public record InitiatePaymentRequest(
            @Schema(description = "Código do plano a ser contratado", example = "PRO")
            String planCode,

            @NotNull
            @Schema(description = "Método de pagamento", example = "PIX")
            PaymentMethod method,

            @Schema(description = "Número do cartão de crédito")
            String cardNumber,

            @Schema(description = "Nome impresso no cartão")
            String cardHolderName,

            @Schema(description = "Mês de expiração do cartão", example = "12")
            Integer cardExpMonth,

            @Schema(description = "Ano de expiração do cartão", example = "2028")
            Integer cardExpYear,

            @Schema(description = "Código de segurança CVV", example = "123")
            String cardCvv,

            @Schema(description = "Token pré-gerado do cartão (se houver)")
            String cardToken,

            @Schema(description = "Número de parcelas (para cartão)", example = "1")
            Integer installments,

            @Schema(description = "CPF ou CNPJ do pagador para emissão de NF e Boleto/PIX")
            String customerDocument,

            @Schema(description = "Telefone de contato do pagador")
            String customerPhone
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
    public record CancelSubscriptionRequest(
            String reason
    ) {}
}
