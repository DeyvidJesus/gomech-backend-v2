package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Característica ou cota associada ao plano")
public record PlanFeatureDto(
        @Schema(description = "Código identificador da cota ou módulo", example = "AI_USAGE")
        String featureCode,

        @Schema(description = "Limite máximo numérico (-1 indica ilimitado)", example = "500")
        Long limitValue,

        @Schema(description = "Indica se o módulo/recurso está habilitado", example = "true")
        boolean enabled,

        @Schema(description = "Unidade de medida do recurso", example = "REQUESTS")
        String unitOfMeasure
) {}
