package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para renovação de token de acesso com Refresh Token")
public record RefreshTokenRequest(
        @NotBlank
        @Schema(description = "Token opaco de atualização (Refresh Token)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String refreshToken
) {}
