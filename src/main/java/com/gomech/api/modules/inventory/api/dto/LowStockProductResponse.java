package com.gomech.api.modules.inventory.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockProductResponse(
    UUID productId,
    String skuCode,
    String productName,
    UUID unitId,
    BigDecimal currentQuantityOnHand,
    BigDecimal quantityReserved,
    BigDecimal availableStock,
    BigDecimal minStockThreshold,
    BigDecimal deficit
) {}
