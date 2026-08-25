package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para atualização de perfil cadastral da empresa (CNPJ é imutável)")
public record UpdateCompanyProfileRequest(
        @NotBlank(message = "A Razão Social da empresa é obrigatória")
        @Schema(description = "Razão Social da empresa", example = "GoMech Automotive Solutions Ltda.")
        String name,

        @Schema(description = "Nome Fantasia da empresa", example = "Oficina GoMech Precision")
        String tradeName,

        @Schema(description = "E-mail de contato geral", example = "contato@gomech.com.br")
        String email,

        @Schema(description = "Telefone principal da empresa", example = "(11) 3456-7890")
        String phone,

        @Schema(description = "URL da logomarca oficial da empresa")
        String logoUrl,

        @Schema(description = "Endereço principal da empresa")
        String address
) {
}
