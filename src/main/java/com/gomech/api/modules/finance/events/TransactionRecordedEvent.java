package com.gomech.api.modules.finance.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionRecordedEvent(
        UUID transactionId,
        UUID tenantId,
        UUID unitId,
        UUID accountId,
        UUID categoryId,
        String categoryName,
        String type, // CREDIT, DEBIT
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public TransactionRecordedEvent(
            UUID transactionId,
            UUID tenantId,
            UUID unitId,
            UUID accountId,
            UUID categoryId,
            String categoryName,
            String type,
            BigDecimal amount,
            LocalDate transactionDate,
            String description
    ) {
        this(transactionId, tenantId, unitId, accountId, categoryId, categoryName, type, amount, transactionDate, description, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "finance.transaction.recorded";
    }
}
