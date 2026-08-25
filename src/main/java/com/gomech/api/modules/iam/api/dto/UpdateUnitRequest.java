package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para atualização de dados de uma unidade ou filial")
public record UpdateUnitRequest(
        @NotBlank(message = "O nome da unidade é obrigatório")
        @Schema(description = "Nome da filial/unidade", example = "Filial Zona Sul")
        String name,

        @Schema(description = "Endereço completo da filial", example = "Rua das Flores, 450 - Bairro Sul")
        String address,

        @Schema(description = "Telefone de contato da filial", example = "(11) 98765-4321")
        String phone,

        @Schema(description = "URL da logomarca da filial")
        String logoUrl,

        @Schema(description = "Responsável técnico pela filial", example = "Eng. Marcos Souza")
        String technicalManager,

        @Schema(description = "Indica se esta unidade deve ser considerada a matriz principal")
        boolean isHeadquarters
) {
}
