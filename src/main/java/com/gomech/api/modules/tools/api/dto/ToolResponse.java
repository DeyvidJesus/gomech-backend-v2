package com.gomech.api.modules.tools.api.dto;

import com.gomech.api.modules.tools.domain.ToolStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record ToolResponse(
        UUID id,
        UUID tenantId,
        UUID unitId,
        UUID categoryId,
        String categoryName,
        String assetTag,
        String serialNumber,
        String name,
        String brand,
        String model,
        ToolStatus status,
        UUID currentHolderUserId,
        String currentHolderUserName,
        String locationInUnit,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        Instant lastMaintenanceAt,
        Instant nextMaintenanceDueAt,
        boolean maintenanceOverdue,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
