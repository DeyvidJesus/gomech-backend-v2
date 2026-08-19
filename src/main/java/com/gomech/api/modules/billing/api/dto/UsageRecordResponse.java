package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Registro de consumo de cota no período")
public record UsageRecordResponse(
        @Schema(description = "ID do registro")
        UUID id,

        @Schema(description = "ID do Tenant")
        UUID tenantId,

        @Schema(description = "ID da unidade associada (opcional)")
        UUID unitId,

        @Schema(description = "Dimensão do recurso consumido", example = "AI_USAGE")
        String dimension,

        @Schema(description = "Quantidade consumida no período", example = "120")
        Long amount,

        @Schema(description = "Limite total do plano (-1 se ilimitado)", example = "500")
        Long limit,

        @Schema(description = "Início do período medido")
        OffsetDateTime periodStart,

        @Schema(description = "Fim do período medido")
        OffsetDateTime periodEnd
) {}
