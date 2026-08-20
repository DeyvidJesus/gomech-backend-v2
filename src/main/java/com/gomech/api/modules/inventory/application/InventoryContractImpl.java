package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.InventoryContract;
import com.gomech.api.modules.inventory.api.dto.CreateReservationRequest;
import com.gomech.api.modules.inventory.api.dto.ProductSummaryResponse;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryContractImpl implements InventoryContract {

    private final ProductService productService;
    private final StockService stockService;
    private final StockReservationService reservationService;
    private final StockConsumptionService consumptionService;

    @Override
    public Optional<ProductSummaryResponse> findProductSummary(UUID productId, UUID tenantId) {
        return productService.findProductSummary(productId, tenantId);
    }

    @Override
    public BigDecimal getAvailableStock(UUID productId, UUID unitId, UUID tenantId) {
        return stockService.getAvailableStock(tenantId, unitId, productId);
    }

    @Override
    public StockReservationResponse reserveStock(
        UUID tenantId,
        UUID unitId,
        UUID productId,
        UUID workOrderId,
        UUID workOrderItemId,
        BigDecimal quantity,
        String notes
    ) {
        CreateReservationRequest request = new CreateReservationRequest(
            unitId,
            productId,
            workOrderId,
            workOrderItemId,
            quantity,
            null,
            notes
        );
        return reservationService.createReservation(request, tenantId, null);
    }

    @Override
    public void releaseReservation(UUID tenantId, UUID reservationId) {
        reservationService.releaseReservation(reservationId, tenantId);
    }

    @Override
    public void releaseWorkOrderReservations(UUID tenantId, UUID workOrderId) {
        reservationService.releaseWorkOrderReservations(workOrderId, tenantId);
    }

    @Override
    public void consumeWorkOrderItem(
        UUID tenantId,
        UUID unitId,
        UUID workOrderId,
        UUID workOrderItemId,
        UUID productId,
        BigDecimal quantity,
        UUID userId,
        String idempotencyKey
    ) {
        consumptionService.consumeWorkOrderItem(
            tenantId,
            unitId,
            workOrderId,
            workOrderItemId,
            productId,
            quantity,
            userId,
            idempotencyKey
        );
    }
}
