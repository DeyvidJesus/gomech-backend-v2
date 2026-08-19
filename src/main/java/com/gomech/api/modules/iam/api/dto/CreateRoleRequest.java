package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Requisição para criação de papel (Role) customizado na oficina")
public record CreateRoleRequest(
        @NotBlank(message = "O nome do papel é obrigatório")
        @Schema(description = "Nome do papel customizado", example = "Auditor de Qualidade")
        String name,

        @Schema(description = "Descrição detalhada do papel", example = "Responsável por inspecionar veículos antes da entrega")
        String description,

        @NotEmpty(message = "Pelo menos uma permissão deve ser informada")
        @Schema(description = "Lista de códigos de permissões atribuídas ao papel", example = "[\"OPERATIONS_ORDER_READ\", \"CRM_VEHICLE_READ\"]")
        List<String> permissionCodes
) {
}
