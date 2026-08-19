package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo do cliente para exibição em listagens paginadas e buscas")
public record CustomerSummaryResponse(
        @Schema(description = "Identificador único do cliente")
        UUID id,

        @Schema(description = "Nome completo ou Razão Social")
        String name,

        @Schema(description = "CPF ou CNPJ normalizado")
        String document,

        @Schema(description = "CPF ou CNPJ formatado")
        String formattedDocument,

        @Schema(description = "Telefone de contato")
        String phone,

        @Schema(description = "E-mail de contato")
        String email,

        @Schema(description = "Quantidade de veículos ativos vinculados")
        int vehicleCount,

        @Schema(description = "Data de cadastro")
        OffsetDateTime createdAt
) {
}
