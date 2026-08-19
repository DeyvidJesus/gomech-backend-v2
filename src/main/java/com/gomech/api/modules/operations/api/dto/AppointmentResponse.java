package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
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
        OffsetDateTime scheduledAt,
        OffsetDateTime estimatedEndAt,
        AppointmentStatus status,
        String serviceType,
        String notes,
        String cancellationReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
