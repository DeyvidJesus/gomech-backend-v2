package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id,
        UUID unitId,
        String orderNumber,
        UUID customerId,
        String customerName,
        String customerDocument,
        String customerPhone,
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        Integer vehicleYear,
        UUID quoteId,
        UUID mechanicUserId,
        String mechanicName,
        String serviceBay,
        WorkOrderStatus status,
        Integer startMileage,
        Integer endMileage,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalServicesAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalAmount,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        OffsetDateTime completedAt,
        OffsetDateTime canceledAt,
        String cancellationReason,
        String technicalNotes,
        String diagnosisNotes,
        String customerNotes,
        List<WorkOrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
}
