package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
    UUID id,
    UUID tenantId,
    UUID unitId,
    UUID productId,
    String productSku,
    String productName,
    UUID userId,
    MovementType type,
    Integer quantity,
    MovementReason reason,
    UUID referenceId,
    BigDecimal unitCostPrice,
    BigDecimal unitSellingPrice,
    BigDecimal totalCostPrice,
    String batchNumber,
    String notes,
    String idempotencyKey,
    Instant createdAt
) {}
