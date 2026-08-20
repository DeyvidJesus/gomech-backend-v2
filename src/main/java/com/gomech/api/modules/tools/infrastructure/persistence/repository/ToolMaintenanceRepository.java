package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.domain.MaintenanceStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolMaintenance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolMaintenanceRepository extends JpaRepository<ToolMaintenance, UUID> {

    @Query("SELECT m FROM ToolMaintenance m WHERE m.id = :id AND m.tenantId = :tenantId")
    Optional<ToolMaintenance> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT m FROM ToolMaintenance m WHERE m.tenantId = :tenantId AND m.toolId = :toolId ORDER BY m.createdAt DESC")
    List<ToolMaintenance> findByTenantIdAndToolIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId);

    @Query("SELECT m FROM ToolMaintenance m WHERE m.tenantId = :tenantId " +
            "AND (:unitId IS NULL OR m.unitId = :unitId) " +
            "AND (:status IS NULL OR m.status = :status) " +
            "AND (:toolId IS NULL OR m.toolId = :toolId) " +
            "ORDER BY m.createdAt DESC")
    Page<ToolMaintenance> findAllFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("unitId") UUID unitId,
            @Param("status") MaintenanceStatus status,
            @Param("toolId") UUID toolId,
            Pageable pageable
    );
}
