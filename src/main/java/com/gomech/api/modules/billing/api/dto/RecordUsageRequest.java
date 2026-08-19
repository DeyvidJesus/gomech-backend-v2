package com.gomech.api.modules.billing.api.dto;

import com.gomech.api.core.entitlement.domain.QuotaDimension;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Requisição para registro de consumo de recurso tarifado/controlado")
public record RecordUsageRequest(
        @NotNull
        @Schema(description = "Dimensão do recurso a ser contabilizado", example = "AI_USAGE")
        QuotaDimension dimension,

        @NotNull @Min(1)
        @Schema(description = "Quantidade incremental a ser registrada", example = "1")
        Long amount,

        @Schema(description = "ID da unidade associada (opcional)")
        UUID unitId
) {}
