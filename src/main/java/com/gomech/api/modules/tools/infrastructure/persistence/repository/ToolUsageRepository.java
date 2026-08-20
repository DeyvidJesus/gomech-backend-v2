package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolUsage;
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
public interface ToolUsageRepository extends JpaRepository<ToolUsage, UUID> {

    @Query("SELECT u FROM ToolUsage u WHERE u.id = :id AND u.tenantId = :tenantId")
    Optional<ToolUsage> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM ToolUsage u WHERE u.tenantId = :tenantId AND u.workOrderId = :workOrderId")
    List<ToolUsage> findByTenantIdAndWorkOrderId(@Param("tenantId") UUID tenantId, @Param("workOrderId") UUID workOrderId);

    @Query("SELECT u FROM ToolUsage u WHERE u.tenantId = :tenantId AND u.toolId = :toolId ORDER BY u.checkedOutAt DESC")
    List<ToolUsage> findByTenantIdAndToolIdOrderByCheckedOutAtDesc(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId);

    @Query("SELECT u FROM ToolUsage u WHERE u.tenantId = :tenantId AND u.toolId = :toolId AND u.checkedInAt IS NULL")
    Optional<ToolUsage> findActiveUsageByToolId(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId);

    @Query("SELECT u FROM ToolUsage u WHERE u.tenantId = :tenantId AND (:toolId IS NULL OR u.toolId = :toolId) ORDER BY u.checkedOutAt DESC")
    Page<ToolUsage> findAllByTenantId(@Param("tenantId") UUID tenantId, @Param("toolId") UUID toolId, Pageable pageable);
}
