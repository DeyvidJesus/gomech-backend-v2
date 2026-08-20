package com.gomech.api.modules.inventory.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UnitStockResponse(
    UUID id,
    UUID tenantId,
    UUID unitId,
    UUID productId,
    String productSku,
    String productName,
    BigDecimal quantityOnHand,
    BigDecimal quantityReserved,
    BigDecimal availableStock,
    BigDecimal minStock,
    BigDecimal maxStock,
    String shelfLocation,
    Instant updatedAt
) {}
