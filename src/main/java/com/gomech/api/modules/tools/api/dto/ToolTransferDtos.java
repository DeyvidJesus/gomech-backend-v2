package com.gomech.api.modules.tools.api.dto;

import com.gomech.api.modules.tools.domain.ToolTransferStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ToolTransferDtos {

    @Builder
    public record Create(
            @NotNull UUID toolId,
            @NotNull UUID destinationUnitId,
            String notes
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            String transferNumber,
            UUID toolId,
            String toolName,
            String toolAssetTag,
            UUID sourceUnitId,
            UUID destinationUnitId,
            ToolTransferStatus status,
            UUID requestedByUserId,
            UUID receivedByUserId,
            Instant sentAt,
            Instant receivedAt,
            String notes,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
