package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateReservationRequest;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;
import com.gomech.api.modules.inventory.domain.InsufficientStockException;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.domain.StockReservationNotFoundException;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

    private final StockReservationRepository reservationRepository;
    private final UnitStockRepository unitStockRepository;
    private final ProductRepository productRepository;

    @Transactional
    public StockReservationResponse createReservation(CreateReservationRequest request, UUID tenantId, UUID userId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.productId(), tenantId)
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        UnitStock stock = unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
            tenantId, request.unitId(), request.productId()
        ).orElseGet(() -> {
            UnitStock newStock = UnitStock.builder()
                .tenantId(tenantId)
                .unitId(request.unitId())
                .productId(request.productId())
                .quantityOnHand(BigDecimal.ZERO)
                .quantityReserved(BigDecimal.ZERO)
                .minStock(BigDecimal.valueOf(product.getMinStock()))
                .build();
            return unitStockRepository.save(newStock);
        });

        BigDecimal available = stock.getAvailableStock();
        if (available.compareTo(request.quantity()) < 0) {
            throw new InsufficientStockException(request.productId(), request.quantity(), available);
        }

        // Incrementa o saldo reservado sem alterar o saldo físico (on-hand)
        stock.setQuantityReserved(stock.getQuantityReserved().add(request.quantity()));
        unitStockRepository.save(stock);

        StockReservation reservation = StockReservation.builder()
            .tenantId(tenantId)
            .unitId(request.unitId())
            .productId(request.productId())
            .workOrderId(request.workOrderId())
            .workOrderItemId(request.workOrderItemId())
            .quantity(request.quantity())
            .status(ReservationStatus.CREATED)
            .expiresAt(request.expiresAt())
            .createdByUserId(userId)
            .notes(request.notes())
            .build();

        StockReservation saved = reservationRepository.save(reservation);
        log.info("Reserva de estoque {} criada para produto {} (qtd: {}) no tenant {}",
            saved.getId(), request.productId(), request.quantity(), tenantId);

        return toResponse(saved, product);
    }

    @Transactional
    public void releaseReservation(UUID reservationId, UUID tenantId) {
        StockReservation reservation = reservationRepository.findByIdAndTenantId(reservationId, tenantId)
            .orElseThrow(() -> new StockReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.CREATED) {
            log.warn("Tentativa de liberar reserva {} com status {}", reservationId, reservation.getStatus());
            return;
        }

        unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
            tenantId, reservation.getUnitId(), reservation.getProductId()
        ).ifPresent(stock -> {
            BigDecimal newReserved = stock.getQuantityReserved().subtract(reservation.getQuantity());
            if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
                newReserved = BigDecimal.ZERO;
            }
            stock.setQuantityReserved(newReserved);
            unitStockRepository.save(stock);
        });

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservationRepository.save(reservation);
        log.info("Reserva de estoque {} liberada com sucesso", reservationId);
    }

    @Transactional
    public void releaseWorkOrderReservations(UUID workOrderId, UUID tenantId) {
        List<StockReservation> reservations = reservationRepository.findAllByTenantIdAndWorkOrderIdAndStatus(
            tenantId, workOrderId, ReservationStatus.CREATED
        );

        for (StockReservation res : reservations) {
            releaseReservation(res.getId(), tenantId);
        }
    }

    @Transactional(readOnly = true)
    public List<StockReservationResponse> listReservationsByWorkOrder(UUID workOrderId, UUID tenantId) {
        return reservationRepository.findAllByTenantIdAndWorkOrderId(tenantId, workOrderId)
            .stream()
            .map(r -> {
                Product p = productRepository.findById(r.getProductId()).orElse(null);
                return toResponse(r, p);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StockReservationResponse> listActiveReservationsByUnit(UUID unitId, UUID tenantId) {
        return reservationRepository.findAllByTenantIdAndUnitIdAndStatus(tenantId, unitId, ReservationStatus.CREATED)
            .stream()
            .map(r -> {
                Product p = productRepository.findById(r.getProductId()).orElse(null);
                return toResponse(r, p);
            })
            .toList();
    }

    private StockReservationResponse toResponse(StockReservation r, Product p) {
        return new StockReservationResponse(
            r.getId(),
            r.getTenantId(),
            r.getUnitId(),
            r.getProductId(),
            p != null ? p.getSkuCode() : "N/A",
            p != null ? p.getName() : "N/A",
            r.getWorkOrderId(),
            r.getWorkOrderItemId(),
            r.getQuantity(),
            r.getStatus(),
            r.getExpiresAt(),
            r.getReleasedAt(),
            r.getConsumedAt(),
            r.getCreatedByUserId(),
            r.getNotes(),
            r.getCreatedAt()
        );
    }
}
