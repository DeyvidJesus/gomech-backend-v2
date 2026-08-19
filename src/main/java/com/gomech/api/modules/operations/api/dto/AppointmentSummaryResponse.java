package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentSummaryResponse(
        UUID id,
        UUID unitId,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleModel,
        OffsetDateTime scheduledAt,
        OffsetDateTime estimatedEndAt,
        AppointmentStatus status,
        String serviceType
) {
}
