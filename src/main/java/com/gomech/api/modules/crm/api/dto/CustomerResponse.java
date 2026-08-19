package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalhes completos do cliente no CRM")
public record CustomerResponse(
        @Schema(description = "Identificador único do cliente")
        UUID id,

        @Schema(description = "Identificador do Tenant")
        UUID tenantId,

        @Schema(description = "Nome completo ou Razão Social")
        String name,

        @Schema(description = "CPF ou CNPJ normalizado (somente dígitos)")
        String document,

        @Schema(description = "CPF ou CNPJ formatado")
        String formattedDocument,

        @Schema(description = "Telefone ou WhatsApp")
        String phone,

        @Schema(description = "E-mail de contato")
        String email,

        @Schema(description = "Endereço completo")
        String address,

        @Schema(description = "Lista de veículos pertencentes ao cliente")
        List<VehicleSummaryResponse> vehicles,

        @Schema(description = "Data de cadastro")
        OffsetDateTime createdAt,

        @Schema(description = "Data da última atualização")
        OffsetDateTime updatedAt
) {
}
