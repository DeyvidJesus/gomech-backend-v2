package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Resumo dos dados do usuário autenticado no contexto atual")
public record UserSummaryDto(
        @Schema(description = "Identificador único do usuário", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Nome completo do usuário", example = "João da Silva")
        String name,

        @Schema(description = "E-mail do usuário", example = "joao@oficina.com.br")
        String email,

        @Schema(description = "Identificador da empresa (Tenant)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID tenantId,

        @Schema(description = "Identificador da unidade física ativa no contexto", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID activeUnitId,

        @Schema(description = "Lista de perfis/roles na unidade ativa", example = "[\"Proprietário\"]")
        List<String> roles,

        @Schema(description = "Lista de permissões granulares na unidade ativa", example = "[\"work_order:create\", \"inventory:read\"]")
        List<String> permissions
) {}
