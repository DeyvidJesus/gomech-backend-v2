package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Permissão do sistema para controle de acesso refinado (PBAC)")
public record PermissionResponse(
        @Schema(description = "Identificador único da permissão")
        UUID id,

        @Schema(description = "Código único da permissão", example = "OPERATIONS_ORDER_EXECUTE")
        String code,

        @Schema(description = "Módulo funcional ao qual a permissão pertence", example = "OPERATIONS")
        String module
) {
}
