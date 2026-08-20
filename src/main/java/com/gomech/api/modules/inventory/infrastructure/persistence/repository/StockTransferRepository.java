package com.gomech.api.modules.inventory.infrastructure.persistence.repository;

import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Optional<StockTransfer> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT t FROM StockTransfer t LEFT JOIN FETCH t.items WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<StockTransfer> findByIdAndTenantIdWithItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT t FROM StockTransfer t
        WHERE t.tenantId = :tenantId
          AND (:unitId IS NULL OR t.sourceUnitId = :unitId OR t.destinationUnitId = :unitId)
        ORDER BY t.createdAt DESC
    """)
    Page<StockTransfer> findAllByTenantAndUnit(
        @Param("tenantId") UUID tenantId,
        @Param("unitId") UUID unitId,
        Pageable pageable
    );

    @Query("SELECT COUNT(t) FROM StockTransfer t WHERE t.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") UUID tenantId);
}
