package com.gomech.api.modules.operations.events;

import com.gomech.api.core.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderCompletedEvent(
        UUID workOrderId,
        UUID tenantId,
        UUID unitId,
        String orderNumber,
        UUID customerId,
        UUID vehicleId,
        UUID quoteId,
        UUID mechanicUserId,
        BigDecimal totalAmount,
        BigDecimal totalPartsAmount,
        BigDecimal totalServicesAmount,
        OffsetDateTime completedAt,
        Integer endMileage,
        int itemCount,
        OffsetDateTime occurredOn
) implements DomainEvent {

    public WorkOrderCompletedEvent(
            UUID workOrderId,
            UUID tenantId,
            UUID unitId,
            String orderNumber,
            UUID customerId,
            UUID vehicleId,
            UUID quoteId,
            UUID mechanicUserId,
            BigDecimal totalAmount,
            BigDecimal totalPartsAmount,
            BigDecimal totalServicesAmount,
            OffsetDateTime completedAt,
            Integer endMileage,
            int itemCount
    ) {
        this(
                workOrderId,
                tenantId,
                unitId,
                orderNumber,
                customerId,
                vehicleId,
                quoteId,
                mechanicUserId,
                totalAmount,
                totalPartsAmount,
                totalServicesAmount,
                completedAt,
                endMileage,
                itemCount,
                OffsetDateTime.now()
        );
    }

    @Override
    public String eventType() {
        return "operations.work_order.completed";
    }
}
