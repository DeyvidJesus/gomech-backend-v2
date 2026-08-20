package com.gomech.api.modules.tools.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ToolCategoryResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        boolean requiresCalibration,
        Integer defaultMaintenanceIntervalDays,
        Instant createdAt,
        Instant updatedAt
) {}
