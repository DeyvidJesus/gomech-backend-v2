package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Requisição para alternar a unidade física ativa do usuário")
public record SwitchUnitRequest(
        @NotNull
        @Schema(description = "Identificador único da unidade de destino", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID unitId
) {}
