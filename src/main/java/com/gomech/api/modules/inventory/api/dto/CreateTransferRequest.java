package com.gomech.api.modules.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateTransferRequest(
    @NotNull(message = "A filial de origem é obrigatória")
    UUID sourceUnitId,

    @NotNull(message = "A filial de destino é obrigatória")
    UUID destinationUnitId,

    String notes,

    @NotEmpty(message = "A transferência deve conter pelo menos um item")
    @Valid
    List<TransferItemRequest> items
) {
    public record TransferItemRequest(
        @NotNull(message = "O ID do produto é obrigatório")
        UUID productId,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        BigDecimal quantity,

        String notes
    ) {}
}
