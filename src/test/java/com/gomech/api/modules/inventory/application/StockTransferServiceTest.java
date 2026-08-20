package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateTransferRequest;
import com.gomech.api.modules.inventory.api.dto.StockTransferResponse;
import com.gomech.api.modules.inventory.domain.InsufficientStockException;
import com.gomech.api.modules.inventory.domain.InvalidStockTransferException;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.TransferStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockTransfer;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockTransferItem;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockTransferRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;

    @Mock
    private UnitStockRepository unitStockRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    private StockTransferService transferService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sourceUnitId = UUID.randomUUID();
    private final UUID destinationUnitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transferService = new StockTransferService(
            transferRepository,
            unitStockRepository,
            productRepository,
            movementRepository
        );
    }

    @Test
    @DisplayName("Deve criar solicitação de transferência com sucesso")
    void shouldCreateStockTransfer() {
        Product product = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("CORREIA-DENT")
            .name("Correia Dentada Gates")
            .build();

        UnitStock sourceStock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(sourceUnitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(10))
            .quantityReserved(BigDecimal.ZERO)
            .build();

        CreateTransferRequest request = new CreateTransferRequest(
            sourceUnitId,
            destinationUnitId,
            "Transferência para reposição filial Norte",
            List.of(new CreateTransferRequest.TransferItemRequest(productId, BigDecimal.valueOf(3), "3 unidades"))
        );

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductId(tenantId, sourceUnitId, productId)).thenReturn(Optional.of(sourceStock));
        when(transferRepository.countByTenantId(tenantId)).thenReturn(0L);
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> {
            StockTransfer t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        StockTransferResponse response = transferService.createTransfer(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.transferNumber()).isEqualTo("TRF-00001");
        assertThat(response.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("Deve rejeitar transferência entre a mesma filial")
    void shouldRejectTransferToSameUnit() {
        CreateTransferRequest request = new CreateTransferRequest(
            sourceUnitId,
            sourceUnitId,
            "Inválido",
            List.of(new CreateTransferRequest.TransferItemRequest(productId, BigDecimal.ONE, null))
        );

        assertThatThrownBy(() -> transferService.createTransfer(request, tenantId, userId))
            .isInstanceOf(InvalidStockTransferException.class)
            .hasMessageContaining("A filial de origem e de destino não podem ser iguais");
    }

    @Test
    @DisplayName("Deve rejeitar transferência quando origem não tem estoque suficiente")
    void shouldRejectWhenSourceStockInsufficient() {
        Product product = Product.builder().id(productId).tenantId(tenantId).build();
        UnitStock sourceStock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(sourceUnitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(2))
            .quantityReserved(BigDecimal.valueOf(1)) // Disponível: 1
            .build();

        CreateTransferRequest request = new CreateTransferRequest(
            sourceUnitId,
            destinationUnitId,
            "Reposição",
            List.of(new CreateTransferRequest.TransferItemRequest(productId, BigDecimal.valueOf(5), null))
        );

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductId(tenantId, sourceUnitId, productId)).thenReturn(Optional.of(sourceStock));

        assertThatThrownBy(() -> transferService.createTransfer(request, tenantId, userId))
            .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("Concluir Transferência: Atualiza saldos de origem e destino e gera movimentações pareadas")
    void shouldCompleteTransferAndAdjustBothUnits() {
        UUID transferId = UUID.randomUUID();
        Product product = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("VELA-01")
            .name("Velas")
            .costPrice(BigDecimal.valueOf(25))
            .sellingPrice(BigDecimal.valueOf(50))
            .minStock(2)
            .build();

        StockTransfer transfer = StockTransfer.builder()
            .id(transferId)
            .tenantId(tenantId)
            .transferNumber("TRF-00002")
            .sourceUnitId(sourceUnitId)
            .destinationUnitId(destinationUnitId)
            .status(TransferStatus.PENDING)
            .items(new ArrayList<>())
            .build();

        StockTransferItem item = StockTransferItem.builder()
            .id(UUID.randomUUID())
            .transfer(transfer)
            .tenantId(tenantId)
            .productId(productId)
            .quantity(BigDecimal.valueOf(4))
            .build();
        transfer.getItems().add(item);

        UnitStock sourceStock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(sourceUnitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(10))
            .quantityReserved(BigDecimal.ZERO)
            .build();

        UnitStock destStock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(destinationUnitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(2))
            .quantityReserved(BigDecimal.ZERO)
            .build();

        when(transferRepository.findByIdAndTenantIdWithItems(transferId, tenantId)).thenReturn(Optional.of(transfer));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, sourceUnitId, productId)).thenReturn(Optional.of(sourceStock));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, destinationUnitId, productId)).thenReturn(Optional.of(destStock));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = transferService.completeTransfer(transferId, tenantId, userId);

        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();

        // Origem deduzida: 10 - 4 = 6
        assertThat(sourceStock.getQuantityOnHand()).isEqualByComparingTo("6");
        // Destino incrementado: 2 + 4 = 6
        assertThat(destStock.getQuantityOnHand()).isEqualByComparingTo("6");

        // Dois registros no ledger de movimentações (OUT e IN)
        ArgumentCaptor<InventoryMovement> movCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, times(2)).save(movCaptor.capture());

        List<InventoryMovement> movements = movCaptor.getAllValues();
        assertThat(movements.get(0).getType()).isEqualTo(MovementType.OUT);
        assertThat(movements.get(0).getReason()).isEqualTo(MovementReason.TRANSFER_OUT);
        assertThat(movements.get(0).getUnitId()).isEqualTo(sourceUnitId);

        assertThat(movements.get(1).getType()).isEqualTo(MovementType.IN);
        assertThat(movements.get(1).getReason()).isEqualTo(MovementReason.TRANSFER_IN);
        assertThat(movements.get(1).getUnitId()).isEqualTo(destinationUnitId);
    }
}
