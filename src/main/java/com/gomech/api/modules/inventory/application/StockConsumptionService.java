package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockConsumptionService {

    private final UnitStockRepository unitStockRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;

    @Transactional
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
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : "WO_CONSUME_" + workOrderId + "_" + (workOrderItemId != null ? workOrderItemId : productId);

        // 1. Verificação de Idempotência
        if (movementRepository.existsByTenantIdAndIdempotencyKey(tenantId, effectiveKey)) {
            log.warn("Consumo de estoque já processado anteriormente para chave {}", effectiveKey);
            return;
        }

        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // 2. Localização e atualização do estoque físico
        UnitStock stock = unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
            tenantId, unitId, productId
        ).orElseGet(() -> {
            UnitStock newStock = UnitStock.builder()
                .tenantId(tenantId)
                .unitId(unitId)
                .productId(productId)
                .quantityOnHand(BigDecimal.ZERO)
                .quantityReserved(BigDecimal.ZERO)
                .minStock(BigDecimal.valueOf(product.getMinStock()))
                .build();
            return unitStockRepository.save(newStock);
        });

        // Baixa no saldo físico (on-hand)
        BigDecimal newOnHand = stock.getQuantityOnHand().subtract(quantity);
        stock.setQuantityOnHand(newOnHand);

        // 3. Se houver reserva associada, consome a reserva correspondente
        if (workOrderItemId != null) {
            Optional<StockReservation> optRes = reservationRepository.findByTenantIdAndWorkOrderIdAndWorkOrderItemIdAndStatus(
                tenantId, workOrderId, workOrderItemId, ReservationStatus.CREATED
            );
            if (optRes.isPresent()) {
                StockReservation res = optRes.get();
                res.setStatus(ReservationStatus.CONSUMED);
                res.setConsumedAt(Instant.now());
                reservationRepository.save(res);

                BigDecimal newReserved = stock.getQuantityReserved().subtract(res.getQuantity());
                if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
                    newReserved = BigDecimal.ZERO;
                }
                stock.setQuantityReserved(newReserved);
            }
        }

        unitStockRepository.save(stock);

        // 4. Registro imutável de movimentação no ledger
        InventoryMovement movement = InventoryMovement.builder()
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .userId(userId)
            .type(MovementType.OUT)
            .quantity(quantity.intValue())
            .reason(MovementReason.WORK_ORDER_CONSUMPTION)
            .referenceId(workOrderId)
            .unitCostPrice(product.getCostPrice())
            .unitSellingPrice(product.getSellingPrice())
            .totalCostPrice(product.getCostPrice().multiply(quantity))
            .idempotencyKey(effectiveKey)
            .notes("Consumo confirmado para OS " + workOrderId + (workOrderItemId != null ? " (item " + workOrderItemId + ")" : ""))
            .build();

        movementRepository.save(movement);

        log.info("Consumo de estoque realizado com sucesso para produto {} (qtd: {}) na OS {} [chave: {}]",
            productId, quantity, workOrderId, effectiveKey);
    }
}
