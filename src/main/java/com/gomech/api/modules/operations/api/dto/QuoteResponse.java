package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        UUID unitId,
        UUID customerId,
        String customerName,
        String customerDocument,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        Integer vehicleYear,
        UUID inspectionId,
        UUID appointmentId,
        UUID createdByUserId,
        UUID approvedByUserId,
        OffsetDateTime approvedAt,
        QuoteStatus status,
        CustomerApprovalStatus customerApprovalStatus,
        OffsetDateTime customerDecisionAt,
        String customerDecisionNotes,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalLaborAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalAmount,
        OffsetDateTime validUntil,
        String notes,
        String termsAndConditions,
        List<QuoteItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
}
