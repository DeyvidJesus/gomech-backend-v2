package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para autenticação via e-mail e senha")
public record LoginRequest(
        @NotBlank @Email
        @Schema(description = "E-mail cadastrado do usuário", example = "joao@oficina.com.br")
        String email,

        @NotBlank
        @Schema(description = "Senha em texto plano enviada sobre canal seguro TLS", example = "Password@123")
        String password
) {}
