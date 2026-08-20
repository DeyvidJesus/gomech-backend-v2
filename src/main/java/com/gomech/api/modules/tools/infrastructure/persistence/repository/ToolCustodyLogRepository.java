package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ToolCustodyLogRepository extends JpaRepository<ToolCustodyLog, UUID> {

    @Query("SELECT l FROM ToolCustodyLog l WHERE l.tenantId = :tenantId AND l.toolId = :toolId ORDER BY l.createdAt DESC")
    List<ToolCustodyLog> findByTenantIdAndToolIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId);

    @Query("SELECT l FROM ToolCustodyLog l WHERE l.tenantId = :tenantId AND (:toolId IS NULL OR l.toolId = :toolId) ORDER BY l.createdAt DESC")
    Page<ToolCustodyLog> findAllByTenantId(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId, Pageable pageable);
}
