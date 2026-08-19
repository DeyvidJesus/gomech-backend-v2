package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderItemStatus;
import com.gomech.api.modules.operations.domain.WorkOrderItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveWorkOrderItemRequest(
        UUID id,

        @NotNull(message = "O tipo do item (PART ou SERVICE) é obrigatório")
        WorkOrderItemType type,

        UUID productId,

        UUID assignedMechanicId,

        @NotBlank(message = "O nome do item/serviço é obrigatório")
        @Size(max = 150, message = "O nome do item deve ter no máximo 150 caracteres")
        String name,

        String description,

        WorkOrderItemStatus status,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade mínima é 0.01")
        BigDecimal quantity,

        @NotNull(message = "O preço unitário é obrigatório")
        @DecimalMin(value = "0.00", message = "O preço unitário não pode ser negativo")
        BigDecimal unitPrice,

        BigDecimal discountAmount,

        BigDecimal taxRate
) {
}
