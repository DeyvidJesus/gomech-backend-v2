package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateAppointmentRequest(
        @NotNull(message = "A data e hora do agendamento são obrigatórias")
        OffsetDateTime scheduledAt,

        OffsetDateTime estimatedEndAt,

        @Size(max = 100, message = "O tipo de serviço deve ter no máximo 100 caracteres")
        String serviceType,

        String notes
) {
}
