package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
    UUID id,
    String skuCode,
    String name,
    String category,
    String barcode,
    String brand,
    UnitOfMeasure unitOfMeasure,
    BigDecimal costPrice,
    BigDecimal sellingPrice,
    Integer minStock,
    boolean active
) {}
