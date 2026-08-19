package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateWorkOrderRequest(
        @NotNull(message = "A unidade é obrigatória")
        UUID unitId,

        @NotNull(message = "O cliente é obrigatório")
        UUID customerId,

        @NotNull(message = "O veículo é obrigatório")
        UUID vehicleId,

        UUID quoteId,

        UUID mechanicUserId,

        @Size(max = 50, message = "O box/baia deve ter no máximo 50 caracteres")
        String serviceBay,

        Integer startMileage,

        OffsetDateTime startDate,

        OffsetDateTime endDate,

        String technicalNotes,

        String diagnosisNotes,

        String customerNotes,

        @Valid
        List<SaveWorkOrderItemRequest> items
) {
}
