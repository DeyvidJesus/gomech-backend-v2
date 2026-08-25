package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Requisição para cadastro inicial e onboarding de oficina e proprietário com início imediato de período de trial")
public record RegisterWorkshopRequest(
        @NotBlank
        @Schema(description = "Nome fantasia da oficina mecânica", example = "Oficina Turbo Power")
        String workshopName,

        @Schema(description = "CNPJ oficial da empresa (Receita Federal)", example = "12345678000190")
        String cnpj,

        @Schema(description = "Telefone da oficina", example = "(11) 3456-7890")
        String phone,

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
        String password,

        @Schema(description = "Código do plano selecionado para avaliação", example = "PRO")
        String planCode
) {
    public RegisterWorkshopRequest(String workshopName, String address, Integer bays, List<String> services, String ownerName, String email, String password) {
        this(workshopName, null, null, address, bays, services, ownerName, email, password, "STARTER");
    }
}
