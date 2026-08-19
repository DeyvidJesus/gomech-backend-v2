package com.gomech.api.modules.operations.api.dto;

import java.time.OffsetDateTime;

public record UpdateQuoteRequest(
        OffsetDateTime validUntil,
        String notes,
        String termsAndConditions
) {
}
