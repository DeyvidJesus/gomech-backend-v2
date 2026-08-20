package com.gomech.api.modules.finance.api.dto;

import com.gomech.api.modules.finance.domain.PayableStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class PayableDtos {

    @Builder
    public record Create(
            @NotNull UUID unitId,
            @NotBlank String supplierName,
            UUID inventoryPurchaseId,
            @NotBlank String description,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate dueDate,
            UUID categoryId,
            String paymentMethod,
            String sourceCorrelationId,
            String notes
    ) {}

    @Builder
    public record Settle(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.01") BigDecimal paidAmount,
            LocalDate paymentDate,
            String paymentMethod,
            String notes
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            UUID unitId,
            String supplierName,
            UUID inventoryPurchaseId,
            String description,
            BigDecimal amount,
            BigDecimal paidAmount,
            LocalDate dueDate,
            Instant paidAt,
            PayableStatus status,
            String paymentMethod,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            String sourceCorrelationId,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
