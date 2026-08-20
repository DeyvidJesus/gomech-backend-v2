package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockReservationResponse(
    UUID id,
    UUID tenantId,
    UUID unitId,
    UUID productId,
    String productSku,
    String productName,
    UUID workOrderId,
    UUID workOrderItemId,
    BigDecimal quantity,
    ReservationStatus status,
    Instant expiresAt,
    Instant releasedAt,
    Instant consumedAt,
    UUID createdByUserId,
    String notes,
    Instant createdAt
) {}
