package com.gomech.api.modules.inventory.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryPurchaseCreatedEvent(
        UUID purchaseId,
        UUID tenantId,
        UUID unitId,
        String supplierName,
        String invoiceNumber,
        BigDecimal totalAmount,
        LocalDate dueDate,
        String description,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public InventoryPurchaseCreatedEvent(
            UUID purchaseId,
            UUID tenantId,
            UUID unitId,
            String supplierName,
            String invoiceNumber,
            BigDecimal totalAmount,
            LocalDate dueDate,
            String description
    ) {
        this(purchaseId, tenantId, unitId, supplierName, invoiceNumber, totalAmount, dueDate, description, OffsetDateTime.now());
    }

    @Override
    public String eventType() {
        return "inventory.purchase.created";
    }
}
