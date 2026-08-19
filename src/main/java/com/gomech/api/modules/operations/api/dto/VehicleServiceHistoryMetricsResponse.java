package com.gomech.api.modules.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VehicleServiceHistoryMetricsResponse(
        int totalServicesCount,
        BigDecimal totalSpent,
        BigDecimal averageTicket,
        OffsetDateTime firstServiceDate,
        OffsetDateTime lastServiceDate,
        Integer lastRecordedMileage,
        int totalPartsReplacedCount
) {}
