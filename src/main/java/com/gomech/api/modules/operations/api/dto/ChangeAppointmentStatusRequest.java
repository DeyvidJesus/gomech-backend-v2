package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeAppointmentStatusRequest(
        @NotNull(message = "O status de destino é obrigatório")
        AppointmentStatus status,

        String cancellationReason
) {
}
