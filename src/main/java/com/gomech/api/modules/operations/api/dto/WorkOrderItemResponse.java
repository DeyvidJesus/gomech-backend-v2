package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderItemStatus;
import com.gomech.api.modules.operations.domain.WorkOrderItemType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderItemResponse(
        UUID id,
        UUID workOrderId,
        WorkOrderItemType type,
        UUID productId,
        UUID assignedMechanicId,
        String assignedMechanicName,
        String name,
        String description,
        WorkOrderItemStatus status,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
