package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.QuoteItemType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuoteItemResponse(
        UUID id,
        UUID quoteId,
        QuoteItemType type,
        UUID productId,
        String name,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
