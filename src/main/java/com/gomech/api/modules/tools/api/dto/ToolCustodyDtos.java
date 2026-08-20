package com.gomech.api.modules.tools.api.dto;

import com.gomech.api.modules.tools.domain.CustodyEventType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ToolCustodyDtos {

    @Builder
    public record CheckOut(
            @NotNull UUID toolId,
            @NotNull UUID mechanicUserId,
            UUID workOrderId,
            String notes
    ) {}

    @Builder
    public record CheckIn(
            @NotNull UUID toolId,
            String locationInUnit,
            String notes
    ) {}

    @Builder
    public record Assign(
            @NotNull UUID toolId,
            @NotNull UUID toUserId,
            String notes
    ) {}

    @Builder
    public record CustodyLogResponse(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID toolId,
            String toolName,
            String toolAssetTag,
            UUID fromUserId,
            String fromUserName,
            UUID toUserId,
            String toUserName,
            CustodyEventType eventType,
            String notes,
            Instant createdAt
    ) {}
}
