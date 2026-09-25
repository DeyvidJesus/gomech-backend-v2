package com.gomech.api.modules.ai.api.dto;

import com.gomech.api.modules.ai.domain.AiActionProposalStatus;
import com.gomech.api.modules.ai.domain.AiActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AiActionDtos {

    private AiActionDtos() {}

    @Builder
    public record CreateAiActionProposalRequest(
            @NotNull(message = "O tipo de ação é obrigatório.")
            AiActionType actionType,

            String targetResourceType,

            UUID targetResourceId,

            @NotBlank(message = "O título da proposta é obrigatório.")
            @Size(max = 255)
            String title,

            String summary,

            @NotBlank(message = "O payload da proposta em JSON é obrigatório.")
            String payloadJson,

            Integer ttlMinutes
    ) {}

    @Builder
    public record ConfirmAiActionRequest(
            String confirmationNotes,
            String adjustedPayloadJson
    ) {}

    @Builder
    public record RejectAiActionRequest(
            @NotBlank(message = "O motivo da rejeição é obrigatório.")
            @Size(max = 500)
            String rejectionReason
    ) {}

    @Builder
    public record AiActionProposalResponse(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID actorUserId,
            AiActionType actionType,
            String targetResourceType,
            UUID targetResourceId,
            String title,
            String summary,
            String payloadJson,
            AiActionProposalStatus status,
            String rejectionReason,
            OffsetDateTime expiresAt,
            boolean isExpired,
            UUID confirmedByUserId,
            OffsetDateTime confirmedAt,
            OffsetDateTime executedAt,
            String executionResultJson,
            Long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    @Builder
    public record AiActionExecutionResult(
            boolean success,
            String message,
            String targetResourceType,
            UUID targetResourceId,
            Object details
    ) {}
}
