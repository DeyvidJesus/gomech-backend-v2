package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para alteração ou contratação de plano")
public record ChangePlanRequest(
        @NotBlank
        @Schema(description = "Código do plano de destino", example = "PRO")
        String planCode
) {}
