package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateWorkOrderRequest(
        UUID mechanicUserId,

        @Size(max = 50, message = "O box/baia deve ter no máximo 50 caracteres")
        String serviceBay,

        Integer startMileage,

        OffsetDateTime startDate,

        OffsetDateTime endDate,

        String technicalNotes,

        String diagnosisNotes,

        String customerNotes
) {
}
