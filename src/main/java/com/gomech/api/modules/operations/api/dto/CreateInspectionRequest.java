package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.FuelLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record CreateInspectionRequest(
        @NotNull(message = "O ID da unidade é obrigatório")
        UUID unitId,

        @NotNull(message = "O ID do cliente é obrigatório")
        UUID customerId,

        @NotNull(message = "O ID do veículo é obrigatório")
        UUID vehicleId,

        UUID appointmentId,

        FuelLevel fuelLevel,

        @PositiveOrZero(message = "A quilometragem deve ser um valor positivo")
        Integer currentMileage,

        String generalNotes,

        @Valid
        List<SaveInspectionItemRequest> items
) {
}
