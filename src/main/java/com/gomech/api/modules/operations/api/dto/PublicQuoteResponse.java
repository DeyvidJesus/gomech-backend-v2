package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicQuoteResponse(
        UUID id,
        QuoteStatus status,
        CustomerApprovalStatus customerApprovalStatus,
        String workshopName,
        String workshopAddress,
        String customerName,
        String customerPhone,
        String customerEmail,
        String vehiclePlate,
        String vehicleModel,
        Integer vehicleYear,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalLaborAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalAmount,
        OffsetDateTime validUntil,
        String notes,
        String termsAndConditions,
        String customerDecisionNotes,
        OffsetDateTime customerDecisionAt,
        List<PublicQuoteItemResponse> items,
        OffsetDateTime createdAt
) {}
