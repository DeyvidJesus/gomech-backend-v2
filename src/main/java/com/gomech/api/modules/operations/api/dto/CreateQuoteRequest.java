package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateQuoteRequest(
        @NotNull(message = "A unidade é obrigatória.")
        UUID unitId,

        @NotNull(message = "O cliente é obrigatório.")
        UUID customerId,

        @NotNull(message = "O veículo é obrigatório.")
        UUID vehicleId,

        UUID inspectionId,

        UUID appointmentId,

        OffsetDateTime validUntil,

        String notes,

        String termsAndConditions,

        @Valid
        List<SaveQuoteItemRequest> items
) {
}
