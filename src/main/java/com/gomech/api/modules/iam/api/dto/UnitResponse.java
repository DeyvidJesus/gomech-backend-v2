package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Dados de uma unidade/filial da oficina")
public record UnitResponse(
        @Schema(description = "Identificador único da unidade")
        UUID id,

        @Schema(description = "Nome da unidade", example = "Filial Centro")
        String name,

        @Schema(description = "Endereço físico da unidade", example = "Av. Brasil, 1500 - Centro")
        String address,

        @Schema(description = "Telefone de contato da filial", example = "(11) 98765-4321")
        String phone,

        @Schema(description = "URL da logomarca da filial")
        String logoUrl,

        @Schema(description = "Responsável técnico pela filial", example = "Eng. Marcos Souza")
        String technicalManager,

        @Schema(description = "Indica se esta unidade é a matriz principal")
        boolean isHeadquarters,

        @Schema(description = "Identificador da oficina (Tenant)")
        UUID tenantId
) {
}

