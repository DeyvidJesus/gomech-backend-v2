package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull(message = "O ID da unidade é obrigatório")
        UUID unitId,

        @NotNull(message = "O ID do cliente é obrigatório")
        UUID customerId,

        @NotNull(message = "O ID do veículo é obrigatório")
        UUID vehicleId,

        @NotNull(message = "A data e hora do agendamento são obrigatórias")
        OffsetDateTime scheduledAt,

        OffsetDateTime estimatedEndAt,

        @Size(max = 100, message = "O tipo de serviço deve ter no máximo 100 caracteres")
        String serviceType,

        String notes,

        UUID assignedUserId
) {
    public CreateAppointmentRequest(UUID unitId, UUID customerId, UUID vehicleId, OffsetDateTime scheduledAt, OffsetDateTime estimatedEndAt, String serviceType, String notes) {
        this(unitId, customerId, vehicleId, scheduledAt, estimatedEndAt, serviceType, notes, null);
    }
}

