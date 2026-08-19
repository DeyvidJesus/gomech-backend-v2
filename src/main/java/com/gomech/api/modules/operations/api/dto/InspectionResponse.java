package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.FuelLevel;
import com.gomech.api.modules.operations.domain.InspectionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
        UUID id,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        String customerName,
        String customerPhone,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        UUID appointmentId,
        UUID inspectorUserId,
        InspectionStatus status,
        FuelLevel fuelLevel,
        Integer currentMileage,
        String generalNotes,
        int totalItems,
        int okItems,
        int attentionItems,
        int criticalItems,
        List<InspectionItemResponse> items,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
