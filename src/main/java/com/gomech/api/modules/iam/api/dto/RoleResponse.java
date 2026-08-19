package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Representação detalhada de um papel (Role) e suas permissões associadas")
public record RoleResponse(
        @Schema(description = "Identificador único do papel")
        UUID id,

        @Schema(description = "Nome do papel", example = "Gerente")
        String name,

        @Schema(description = "Descrição das responsabilidades do papel", example = "Gestão de equipe e ordens de serviço")
        String description,

        @Schema(description = "Identificador da oficina (Tenant)")
        UUID tenantId,

        @Schema(description = "Lista de códigos de permissões vinculadas ao papel", example = "[\"CRM_CUSTOMER_READ\", \"OPERATIONS_ORDER_WRITE\"]")
        List<String> permissions
) {
}
