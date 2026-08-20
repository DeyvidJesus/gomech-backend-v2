package com.gomech.api.modules.tools.api.dto;

import com.gomech.api.modules.tools.domain.MaintenanceStatus;
import com.gomech.api.modules.tools.domain.MaintenanceType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ToolMaintenanceDtos {

    @Builder
    public record Schedule(
            @NotNull UUID toolId,
            MaintenanceType maintenanceType,
            @NotNull LocalDate scheduledDate,
            String performedByProvider,
            BigDecimal estimatedCost,
            String description
    ) {}

    @Builder
    public record Complete(
            String performedByProvider,
            BigDecimal cost,
            String description,
            String findings,
            LocalDate nextDueDate
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID toolId,
            String toolName,
            String toolAssetTag,
            MaintenanceType maintenanceType,
            MaintenanceStatus status,
            LocalDate scheduledDate,
            Instant performedAt,
            String performedByProvider,
            BigDecimal cost,
            String description,
            String findings,
            LocalDate nextDueDate,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
