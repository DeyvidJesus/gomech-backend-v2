package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.FuelLevel;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateInspectionRequest(
        FuelLevel fuelLevel,

        @PositiveOrZero(message = "A quilometragem deve ser um valor positivo")
        Integer currentMileage,

        String generalNotes
) {
}
