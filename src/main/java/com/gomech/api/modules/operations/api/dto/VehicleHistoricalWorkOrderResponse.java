package com.gomech.api.modules.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VehicleHistoricalWorkOrderResponse(
        UUID workOrderId,
        String orderNumber,
        String serviceBay,
        OffsetDateTime completedAt,
        Integer mileageAtService,
        BigDecimal totalAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalServicesAmount,
        String technicalNotes,
        String customerNotes,
        List<VehicleHistoricalItemResponse> items
) {}
