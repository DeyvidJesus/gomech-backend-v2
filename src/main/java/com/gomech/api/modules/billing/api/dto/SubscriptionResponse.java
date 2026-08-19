package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados da assinatura ativa do Tenant")
public record SubscriptionResponse(
        @Schema(description = "ID único da assinatura")
        UUID id,

        @Schema(description = "ID do Tenant associado")
        UUID tenantId,

        @Schema(description = "Código do plano", example = "PRO")
        String planCode,

        @Schema(description = "Nome do plano", example = "Profissional")
        String planName,

        @Schema(description = "Status da assinatura", example = "ACTIVE")
        String status,

        @Schema(description = "Data do próximo faturamento")
        LocalDate nextBillingDate,

        @Schema(description = "Início do ciclo atual")
        OffsetDateTime currentPeriodStart,

        @Schema(description = "Fim do ciclo atual")
        OffsetDateTime currentPeriodEnd,

        @Schema(description = "Data limite do período de teste (se aplicável)")
        OffsetDateTime trialEndsAt,

        @Schema(description = "Cancelamento agendado para o fim do período")
        boolean cancelAtPeriodEnd,

        @Schema(description = "Cotas e módulos ativos concedidos pela assinatura")
        List<PlanFeatureDto> features
) {}
