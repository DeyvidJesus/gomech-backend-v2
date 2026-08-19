package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteSummaryResponse(
        UUID id,
        UUID unitId,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        UUID inspectionId,
        QuoteStatus status,
        CustomerApprovalStatus customerApprovalStatus,
        BigDecimal totalLaborAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalAmount,
        int itemCount,
        OffsetDateTime validUntil,
        OffsetDateTime createdAt
) {
}
