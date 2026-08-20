package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockConsumptionServiceTest {

    @Mock
    private UnitStockRepository unitStockRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    private StockConsumptionService consumptionService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workOrderId = UUID.randomUUID();
    private final UUID workOrderItemId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumptionService = new StockConsumptionService(
            unitStockRepository,
            productRepository,
            movementRepository,
            reservationRepository
        );
    }

    @Test
    @DisplayName("Consumo Confirmado: Baixa no estoque físico, consome reserva e gera registro contábil imutável")
    void shouldConsumeWorkOrderItemAndRecordMovement() {
        Product product = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("AMORTECEDOR-DIR")
            .name("Amortecedor Dianteiro Direito")
            .costPrice(BigDecimal.valueOf(180))
            .sellingPrice(BigDecimal.valueOf(320))
            .build();

        UnitStock stock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(6))
            .quantityReserved(BigDecimal.valueOf(2))
            .build();

        StockReservation reservation = StockReservation.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .workOrderId(workOrderId)
            .workOrderItemId(workOrderItemId)
            .quantity(BigDecimal.valueOf(2))
            .status(ReservationStatus.CREATED)
            .build();

        String idempotencyKey = "WO_CONSUME_" + workOrderId + "_" + workOrderItemId;

        when(movementRepository.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)).thenReturn(false);
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, unitId, productId)).thenReturn(Optional.of(stock));
        when(reservationRepository.findByTenantIdAndWorkOrderIdAndWorkOrderItemIdAndStatus(tenantId, workOrderId, workOrderItemId, ReservationStatus.CREATED))
            .thenReturn(Optional.of(reservation));

        consumptionService.consumeWorkOrderItem(
            tenantId, unitId, workOrderId, workOrderItemId, productId, BigDecimal.valueOf(2), userId, idempotencyKey
        );

        // Validação da dedução do saldo físico e liberação da reserva
        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("4"); // 6 - 2 = 4
        assertThat(stock.getQuantityReserved()).isEqualByComparingTo("0"); // 2 - 2 = 0
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONSUMED);
        assertThat(reservation.getConsumedAt()).isNotNull();

        // Validação da movimentação imutável
        ArgumentCaptor<InventoryMovement> movCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(movCaptor.capture());
        InventoryMovement mov = movCaptor.getValue();
        assertThat(mov.getType()).isEqualTo(MovementType.OUT);
        assertThat(mov.getReason()).isEqualTo(MovementReason.WORK_ORDER_CONSUMPTION);
        assertThat(mov.getQuantity()).isEqualTo(2);
        assertThat(mov.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(mov.getTotalCostPrice()).isEqualByComparingTo("360.00");
    }

    @Test
    @DisplayName("Garantia de Idempotência: Replay com mesma chave não gera duplicidade de dedução")
    void shouldBeIdempotentWhenSameKeyReplayed() {
        String idempotencyKey = "WO_CONSUME_DUPLICATE_KEY";

        when(movementRepository.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)).thenReturn(true);

        consumptionService.consumeWorkOrderItem(
            tenantId, unitId, workOrderId, workOrderItemId, productId, BigDecimal.valueOf(1), userId, idempotencyKey
        );

        // Nenhuma atualização ou novo movimento é salvo
        verify(unitStockRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }
}
