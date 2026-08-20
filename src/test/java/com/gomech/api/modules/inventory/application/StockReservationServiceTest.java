package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateReservationRequest;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;
import com.gomech.api.modules.inventory.domain.InsufficientStockException;
import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private UnitStockRepository unitStockRepository;

    @Mock
    private ProductRepository productRepository;

    private StockReservationService reservationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID workOrderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reservationService = new StockReservationService(reservationRepository, unitStockRepository, productRepository);
    }

    @Test
    @DisplayName("Invariante de Reserva: Reserva incrementa quantityReserved e NÃO altera quantityOnHand")
    void shouldReserveStockWithoutDecrementingOnHand() {
        Product product = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("PAST-FREIO-01")
            .name("Pastilha de Freio Dianteira")
            .minStock(2)
            .build();

        UnitStock stock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(10))
            .quantityReserved(BigDecimal.valueOf(2)) // Disponível: 8
            .build();

        CreateReservationRequest request = new CreateReservationRequest(
            unitId,
            productId,
            workOrderId,
            UUID.randomUUID(),
            BigDecimal.valueOf(3),
            null,
            "Reserva para troca de freios"
        );

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, unitId, productId)).thenReturn(Optional.of(stock));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> {
            StockReservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        StockReservationResponse response = reservationService.createReservation(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.CREATED);
        assertThat(response.quantity()).isEqualByComparingTo("3");

        // Validação da invariante de domínio
        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("10"); // Saldo físico inalterado!
        assertThat(stock.getQuantityReserved()).isEqualByComparingTo("5"); // 2 + 3 = 5
        assertThat(stock.getAvailableStock()).isEqualByComparingTo("5"); // 10 - 5 = 5 disponível
        verify(unitStockRepository).save(stock);
    }

    @Test
    @DisplayName("Deve rejeitar reserva quando a quantidade disponível for insuficiente")
    void shouldRejectReservationWhenInsufficientStock() {
        Product product = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("BATERIA-60A")
            .name("Bateria 60Ah Heliar")
            .build();

        UnitStock stock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(5))
            .quantityReserved(BigDecimal.valueOf(4)) // Disponível: apenas 1
            .build();

        CreateReservationRequest request = new CreateReservationRequest(
            unitId,
            productId,
            workOrderId,
            null,
            BigDecimal.valueOf(2), // Solicitado: 2 > 1
            null,
            null
        );

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, unitId, productId)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> reservationService.createReservation(request, tenantId, userId))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    @DisplayName("Deve liberar reserva restaurando o saldo disponível")
    void shouldReleaseReservationAndRestoreAvailability() {
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
            .id(reservationId)
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .quantity(BigDecimal.valueOf(4))
            .status(ReservationStatus.CREATED)
            .build();

        UnitStock stock = UnitStock.builder()
            .tenantId(tenantId)
            .unitId(unitId)
            .productId(productId)
            .quantityOnHand(BigDecimal.valueOf(10))
            .quantityReserved(BigDecimal.valueOf(4))
            .build();

        when(reservationRepository.findByIdAndTenantId(reservationId, tenantId)).thenReturn(Optional.of(reservation));
        when(unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(tenantId, unitId, productId)).thenReturn(Optional.of(stock));

        reservationService.releaseReservation(reservationId, tenantId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isNotNull();
        assertThat(stock.getQuantityReserved()).isEqualByComparingTo("0");
        assertThat(stock.getAvailableStock()).isEqualByComparingTo("10");
        verify(unitStockRepository).save(stock);
        verify(reservationRepository).save(reservation);
    }
}
