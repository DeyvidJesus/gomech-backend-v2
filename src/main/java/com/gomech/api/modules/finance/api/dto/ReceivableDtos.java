package com.gomech.api.modules.finance.api.dto;

import com.gomech.api.modules.finance.domain.ReceivableStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ReceivableDtos {

    @Builder
    public record Create(
            @NotNull UUID unitId,
            UUID customerId,
            String customerName,
            UUID workOrderId,
            String orderNumber,
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
            UUID customerId,
            String customerName,
            UUID workOrderId,
            String orderNumber,
            String description,
            BigDecimal amount,
            BigDecimal paidAmount,
            LocalDate dueDate,
            Instant receivedAt,
            ReceivableStatus status,
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
