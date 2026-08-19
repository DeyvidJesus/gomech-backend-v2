package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.InspectionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionSummaryResponse(
        UUID id,
        UUID unitId,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleModel,
        UUID appointmentId,
        InspectionStatus status,
        int totalItems,
        int criticalItems,
        int attentionItems,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {
}
