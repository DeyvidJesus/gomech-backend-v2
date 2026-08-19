package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleHistoricalItemResponse(
        UUID id,
        WorkOrderItemType type,
        String name,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount
) {}
