package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Requisição para cadastro inicial e onboarding de oficina e proprietário")
public record RegisterWorkshopRequest(
        @NotBlank
        @Schema(description = "Nome fantasia da oficina mecânica", example = "Oficina Turbo Power")
        String workshopName,

        @NotBlank
        @Schema(description = "Endereço físico da unidade matriz", example = "Av. das Américas, 1000 - Rio de Janeiro")
        String address,

        @NotNull
        @Schema(description = "Quantidade de boxes/elevadores de atendimento", example = "4")
        Integer bays,

        @Schema(description = "Lista de serviços mecânicos prestados", example = "[\"Mecânica Geral\", \"Injeção Eletrônica\"]")
        List<String> services,
        
        @NotBlank
        @Schema(description = "Nome completo do proprietário", example = "Carlos Alberto")
        String ownerName,

        @NotBlank @Email
        @Schema(description = "E-mail de acesso e proprietário", example = "carlos@turbopower.com.br")
        String email,

        @NotBlank
        @Schema(description = "Senha de acesso inicial", example = "Password@123")
        String password
) {}
