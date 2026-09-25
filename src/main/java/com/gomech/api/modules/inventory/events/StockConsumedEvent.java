package com.gomech.api.modules.inventory.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockConsumedEvent(
        UUID tenantId,
        UUID unitId,
        UUID workOrderId,
        UUID workOrderItemId,
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        UUID userId,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public StockConsumedEvent(
            UUID tenantId,
            UUID unitId,
            UUID workOrderId,
            UUID workOrderItemId,
            UUID productId,
            String productName,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalCost,
            UUID userId
    ) {
        this(tenantId, unitId, workOrderId, workOrderItemId, productId, productName, quantity, unitCost, totalCost, userId, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "inventory.stock.consumed";
    }
}
