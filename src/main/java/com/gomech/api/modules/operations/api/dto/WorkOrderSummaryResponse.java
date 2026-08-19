package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderSummaryResponse(
        UUID id,
        UUID unitId,
        String orderNumber,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        UUID quoteId,
        UUID mechanicUserId,
        String mechanicName,
        String serviceBay,
        WorkOrderStatus status,
        BigDecimal totalServicesAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalAmount,
        int itemCount,
        int completedItemCount,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {
}
