package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID tenantId,
    UUID unitId,
    UUID supplierId,
    String skuCode,
    String name,
    String category,
    String barcode,
    String brand,
    UnitOfMeasure unitOfMeasure,
    BigDecimal costPrice,
    BigDecimal sellingPrice,
    Integer minStock,
    Integer currentStockCalculated,
    String locationInWarehouse,
    boolean active,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {}
