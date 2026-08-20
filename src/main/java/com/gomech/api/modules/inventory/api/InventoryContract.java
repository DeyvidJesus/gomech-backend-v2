package com.gomech.api.modules.inventory.api;

import com.gomech.api.modules.inventory.api.dto.ProductSummaryResponse;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface InventoryContract {

    Optional<ProductSummaryResponse> findProductSummary(UUID productId, UUID tenantId);

    BigDecimal getAvailableStock(UUID productId, UUID unitId, UUID tenantId);

    StockReservationResponse reserveStock(
        UUID tenantId,
        UUID unitId,
        UUID productId,
        UUID workOrderId,
        UUID workOrderItemId,
        BigDecimal quantity,
        String notes
    );

    void releaseReservation(UUID tenantId, UUID reservationId);

    void releaseWorkOrderReservations(UUID tenantId, UUID workOrderId);

    void consumeWorkOrderItem(
        UUID tenantId,
        UUID unitId,
        UUID workOrderId,
        UUID workOrderItemId,
        UUID productId,
        BigDecimal quantity,
        UUID userId,
        String idempotencyKey
    );
}
