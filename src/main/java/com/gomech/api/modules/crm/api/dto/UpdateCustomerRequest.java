package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para atualização de um cliente existente")
public record UpdateCustomerRequest(
        @NotBlank(message = "O nome do cliente é obrigatório")
        @Size(max = 255, message = "O nome não pode exceder 255 caracteres")
        @Schema(description = "Nome completo ou Razão Social", example = "Carlos Henrique Silva")
        String name,

        @Schema(description = "CPF (11 dígitos) ou CNPJ (14 dígitos), com ou sem formatação", example = "123.456.789-00")
        String document,

        @Size(max = 50, message = "O telefone não pode exceder 50 caracteres")
        @Schema(description = "Telefone ou WhatsApp de contato", example = "(11) 98765-4321")
        String phone,

        @Email(message = "E-mail com formato inválido")
        @Size(max = 255, message = "O e-mail não pode exceder 255 caracteres")
        @Schema(description = "E-mail de contato", example = "carlos.silva@email.com")
        String email,

        @Schema(description = "Endereço completo", example = "Av. Paulista, 1000, Apto 50 - São Paulo/SP")
        String address
) {
}
