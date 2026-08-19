package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados do plano de assinatura")
public record BillingPlanResponse(
        @Schema(description = "ID único do plano")
        UUID id,

        @Schema(description = "Código do plano", example = "PRO")
        String code,

        @Schema(description = "Nome comercial do plano", example = "Profissional")
        String name,

        @Schema(description = "Descrição dos benefícios")
        String description,

        @Schema(description = "Preço mensal ou periódico em reais", example = "299.90")
        BigDecimal price,

        @Schema(description = "Intervalo de faturamento", example = "MONTHLY")
        String billingInterval,

        @Schema(description = "Status de ativação para novas contratações")
        boolean active,

        @Schema(description = "Lista de cotas e módulos inclusos no plano")
        List<PlanFeatureDto> features
) {}
