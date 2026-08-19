package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Requisição para vincular um papel a um usuário em uma unidade específica")
public record AssignUserRoleRequest(
        @NotNull(message = "O ID do papel é obrigatório")
        @Schema(description = "Identificador do papel a ser atribuído")
        UUID roleId,

        @Schema(description = "Identificador da unidade de atuação (nulo para escopo tenant-wide)")
        UUID unitId
) {
}
