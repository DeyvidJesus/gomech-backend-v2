package com.gomech.api.modules.tools.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ToolUsageDtos {

    @Builder
    public record RecordUsage(
            @NotNull UUID toolId,
            @NotNull UUID workOrderId,
            UUID mechanicUserId,
            String notes
    ) {}

    @Builder
    public record UsageResponse(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID toolId,
            String toolName,
            String toolAssetTag,
            UUID workOrderId,
            UUID mechanicUserId,
            String mechanicUserName,
            Instant checkedOutAt,
            Instant checkedInAt,
            String notes,
            Instant createdAt
    ) {}
}
