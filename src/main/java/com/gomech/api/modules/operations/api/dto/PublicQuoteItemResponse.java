package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.QuoteItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicQuoteItemResponse(
        UUID id,
        QuoteItemType type,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {}
