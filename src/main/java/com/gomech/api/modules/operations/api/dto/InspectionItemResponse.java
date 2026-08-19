package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.InspectionCategory;
import com.gomech.api.modules.operations.domain.InspectionItemStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionItemResponse(
        UUID id,
        InspectionCategory category,
        String name,
        InspectionItemStatus status,
        String notes,
        String recommendedAction,
        String photoUrls,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
