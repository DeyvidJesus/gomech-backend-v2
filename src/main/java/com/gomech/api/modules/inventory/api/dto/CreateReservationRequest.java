package com.gomech.api.modules.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateReservationRequest(
    @NotNull(message = "O ID da unidade é obrigatório")
    UUID unitId,

    @NotNull(message = "O ID do produto é obrigatório")
    UUID productId,

    UUID workOrderId,
    UUID workOrderItemId,

    @NotNull(message = "A quantidade a reservar é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    BigDecimal quantity,

    Instant expiresAt,
    String notes
) {}
