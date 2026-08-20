package com.gomech.api.modules.inventory.infrastructure.persistence.repository;

import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitStockRepository extends JpaRepository<UnitStock, UUID> {

    Optional<UnitStock> findByTenantIdAndUnitIdAndProductId(UUID tenantId, UUID unitId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT us FROM UnitStock us WHERE us.tenantId = :tenantId AND us.unitId = :unitId AND us.productId = :productId")
    Optional<UnitStock> findByTenantIdAndUnitIdAndProductIdForUpdate(
        @Param("tenantId") UUID tenantId,
        @Param("unitId") UUID unitId,
        @Param("productId") UUID productId
    );

    List<UnitStock> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<UnitStock> findAllByTenantIdAndProductId(UUID tenantId, UUID productId);

    @Query("""
        SELECT us FROM UnitStock us
        WHERE us.tenantId = :tenantId
          AND us.unitId = :unitId
          AND us.quantityOnHand <= us.minStock
    """)
    List<UnitStock> findLowStockByTenantAndUnit(
        @Param("tenantId") UUID tenantId,
        @Param("unitId") UUID unitId
    );
}
