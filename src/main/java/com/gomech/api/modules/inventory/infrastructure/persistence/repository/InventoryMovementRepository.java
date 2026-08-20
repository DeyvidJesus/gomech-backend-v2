package com.gomech.api.modules.inventory.infrastructure.persistence.repository;

import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Optional<InventoryMovement> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.tenantId = :tenantId
          AND (:unitId IS NULL OR m.unitId = :unitId)
          AND (:productId IS NULL OR m.productId = :productId)
          AND (:type IS NULL OR m.type = :type)
          AND (:reason IS NULL OR m.reason = :reason)
        ORDER BY m.createdAt DESC
    """)
    Page<InventoryMovement> findAllByTenantWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("unitId") UUID unitId,
        @Param("productId") UUID productId,
        @Param("type") MovementType type,
        @Param("reason") MovementReason reason,
        Pageable pageable
    );
}
