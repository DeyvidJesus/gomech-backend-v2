package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.MovementReason;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdjustStockRequest(
    @NotNull(message = "O ID da unidade é obrigatório")
    UUID unitId,

    @NotNull(message = "O ID do produto é obrigatório")
    UUID productId,

    @NotNull(message = "A nova quantidade física (on-hand) é obrigatória")
    BigDecimal newQuantityOnHand,

    @NotNull(message = "O motivo do ajuste é obrigatório")
    MovementReason reason,

    String notes
) {}
