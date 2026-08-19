package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de autenticação contendo tokens e informações contextuais do usuário")
public record AuthResponse(
        @Schema(description = "Token de acesso assinado (JWT) de curta duração", example = "eyJhbGciOiJIUzM4NCJ9...")
        String accessToken,

        @Schema(description = "Token opaco de atualização (Refresh Token) rotacionável", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String refreshToken,

        @Schema(description = "Tipo do token emitido", example = "Bearer")
        String tokenType,

        @Schema(description = "Tempo de vida do token de acesso em segundos", example = "900")
        long expiresIn,

        @Schema(description = "Dados resumidos do usuário autenticado")
        UserSummaryDto user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, "Bearer", expiresIn, null);
    }
}
