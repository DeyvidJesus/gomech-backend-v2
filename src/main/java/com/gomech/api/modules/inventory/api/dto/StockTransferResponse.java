package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockTransferResponse(
    UUID id,
    UUID tenantId,
    String transferNumber,
    UUID sourceUnitId,
    UUID destinationUnitId,
    TransferStatus status,
    String notes,
    UUID requestedByUserId,
    UUID receivedByUserId,
    Instant completedAt,
    Instant canceledAt,
    String cancellationReason,
    List<TransferItemResponse> items,
    Instant createdAt
) {
    public record TransferItemResponse(
        UUID id,
        UUID productId,
        String productSku,
        String productName,
        BigDecimal quantity,
        String notes
    ) {}
}
