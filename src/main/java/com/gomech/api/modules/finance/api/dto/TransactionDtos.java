package com.gomech.api.modules.finance.api.dto;

import com.gomech.api.modules.finance.domain.TransactionStatus;
import com.gomech.api.modules.finance.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class TransactionDtos {

    @Builder
    public record Create(
            @NotNull UUID unitId,
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate transactionDate,
            LocalDate competenceDate,
            @NotBlank String description,
            String notes
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            UUID receivableId,
            UUID payableId,
            TransactionType type,
            BigDecimal amount,
            LocalDate transactionDate,
            LocalDate competenceDate,
            String description,
            TransactionStatus status,
            String sourceCorrelationId,
            String notes,
            Instant createdAt,
            UUID createdByUserId
    ) {}
}
