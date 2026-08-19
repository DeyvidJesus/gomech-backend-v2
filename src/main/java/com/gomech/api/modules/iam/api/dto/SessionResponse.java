package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Detalhes de uma sessão ativa do usuário")
public record SessionResponse(
        @Schema(description = "Identificador único da sessão", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Identificador da família de tokens para rastreamento de rotação", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID familyId,

        @Schema(description = "Data e hora de criação da sessão")
        OffsetDateTime createdAt,

        @Schema(description = "Data e hora do último uso da sessão")
        OffsetDateTime lastUsedAt,

        @Schema(description = "Data e hora de expiração da sessão")
        OffsetDateTime expiresAt,

        @Schema(description = "Endereço IP de origem da sessão", example = "192.168.1.100")
        String ipAddress,

        @Schema(description = "User-Agent do navegador/cliente", example = "Mozilla/5.0 ...")
        String userAgent,

        @Schema(description = "Informações do dispositivo", example = "Chrome 120 on Linux")
        String deviceInfo,

        @Schema(description = "Indica se esta sessão corresponde à requisição atual")
        boolean isCurrent
) {}
