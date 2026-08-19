package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta contendo a URL de autorização Google e estado assinado")
public record GoogleAuthorizeUrlResponse(
        @Schema(description = "URL completa de redirecionamento para o consentimento Google OAuth 2.0", example = "https://accounts.google.com/o/oauth2/v2/auth?...")
        String authorizationUrl,

        @Schema(description = "Estado assinado para correlação na requisição de callback", example = "eyJhbGciOiJIUzI1NiJ9...")
        String state
) {}
