package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.QuoteItemType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveQuoteItemRequest(
        UUID id,

        QuoteItemType type,

        UUID productId,

        @NotBlank(message = "O nome do item é obrigatório.")
        @Size(max = 150, message = "O nome do item deve ter no máximo 150 caracteres.")
        String name,

        String description,

        @NotNull(message = "A quantidade é obrigatória.")
        @DecimalMin(value = "0.01", message = "A quantidade deve ser maior que zero.")
        BigDecimal quantity,

        @NotNull(message = "O valor unitário é obrigatório.")
        @DecimalMin(value = "0.00", message = "O valor unitário não pode ser negativo.")
        BigDecimal unitPrice,

        @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
        BigDecimal discountAmount,

        @DecimalMin(value = "0.00", message = "A alíquota de imposto não pode ser negativa.")
        @DecimalMax(value = "100.00", message = "A alíquota de imposto não pode exceder 100%.")
        BigDecimal taxRate
) {
}
