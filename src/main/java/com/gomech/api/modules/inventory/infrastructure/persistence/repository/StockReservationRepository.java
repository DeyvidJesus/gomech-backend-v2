package com.gomech.api.modules.inventory.infrastructure.persistence.repository;

import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<StockReservation> findByTenantIdAndWorkOrderIdAndWorkOrderItemIdAndStatus(
        UUID tenantId,
        UUID workOrderId,
        UUID workOrderItemId,
        ReservationStatus status
    );

    List<StockReservation> findAllByTenantIdAndWorkOrderId(UUID tenantId, UUID workOrderId);

    List<StockReservation> findAllByTenantIdAndWorkOrderIdAndStatus(UUID tenantId, UUID workOrderId, ReservationStatus status);

    List<StockReservation> findAllByTenantIdAndProductIdAndStatus(UUID tenantId, UUID productId, ReservationStatus status);

    List<StockReservation> findAllByTenantIdAndUnitIdAndStatus(UUID tenantId, UUID unitId, ReservationStatus status);
}
