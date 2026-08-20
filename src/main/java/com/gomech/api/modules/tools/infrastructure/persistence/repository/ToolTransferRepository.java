package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.domain.ToolTransferStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolTransferRepository extends JpaRepository<ToolTransfer, UUID> {

    @Query("SELECT t FROM ToolTransfer t WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<ToolTransfer> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM ToolTransfer t WHERE t.tenantId = :tenantId " +
            "AND (:unitId IS NULL OR t.sourceUnitId = :unitId OR t.destinationUnitId = :unitId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "ORDER BY t.createdAt DESC")
    Page<ToolTransfer> findAllByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("unitId") UUID unitId,
            @Param("status") ToolTransferStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(t) FROM ToolTransfer t WHERE t.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") UUID tenantId);
}
