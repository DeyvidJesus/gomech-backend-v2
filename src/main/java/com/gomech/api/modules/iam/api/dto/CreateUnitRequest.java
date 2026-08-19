package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para cadastro de nova unidade ou filial")
public record CreateUnitRequest(
        @NotBlank(message = "O nome da unidade é obrigatório")
        @Schema(description = "Nome da nova filial/unidade", example = "Filial Zona Sul")
        String name,

        @Schema(description = "Endereço completo da filial", example = "Rua das Flores, 450 - Bairro Sul")
        String address,

        @Schema(description = "Indica se esta unidade deve ser considerada a matriz principal")
        boolean isHeadquarters
) {
}
