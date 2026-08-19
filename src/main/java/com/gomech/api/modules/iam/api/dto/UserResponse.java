package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Dados do usuário e seus vínculos de papéis e unidades")
public record UserResponse(
        @Schema(description = "Identificador único do usuário")
        UUID id,

        @Schema(description = "Nome completo do usuário", example = "Carlos Mecânico")
        String name,

        @Schema(description = "E-mail de acesso do usuário", example = "carlos@oficina.com.br")
        String email,

        @Schema(description = "Status atual da conta", example = "ACTIVE")
        String status,

        @Schema(description = "Identificador da oficina (Tenant)")
        UUID tenantId,

        @Schema(description = "Lista de papéis atribuídos ao usuário por unidade")
        List<UserRoleDetailDto> roles
) {
    public UserResponse(UUID id, String name, String email, String status) {
        this(id, name, email, status, null, List.of());
    }

    @Schema(description = "Detalhe do papel e unidade vinculado ao usuário")
    public record UserRoleDetailDto(
            UUID roleId,
            String roleName,
            UUID unitId,
            String unitName
    ) {}
}
