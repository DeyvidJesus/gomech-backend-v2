package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição de callback para troca de código de autorização Google OAuth 2.0")
public record GoogleOAuthCallbackRequest(
        @NotBlank
        @Schema(description = "Código de autorização retornado pelo Google", example = "4/0AeanS0b...")
        String code,

        @NotBlank
        @Schema(description = "Estado assinado para validação anti-CSRF e PKCE", example = "eyJhbGciOiJIUzI1NiJ9...")
        String state
) {}
