package com.gomech.api.modules.operations.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VehicleHistoricalInspectionResponse(
        UUID inspectionId,
        OffsetDateTime completedAt,
        Integer mileage,
        String fuelLevel,
        String generalNotes,
        int totalItems,
        int okItemsCount,
        int attentionItemsCount,
        int criticalItemsCount,
        List<String> criticalIssues
) {}
