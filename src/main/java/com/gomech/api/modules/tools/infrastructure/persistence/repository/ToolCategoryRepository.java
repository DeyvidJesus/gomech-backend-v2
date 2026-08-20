package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolCategoryRepository extends JpaRepository<ToolCategory, UUID> {

    @Query("SELECT c FROM ToolCategory c WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<ToolCategory> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM ToolCategory c WHERE c.tenantId = :tenantId ORDER BY c.name ASC")
    List<ToolCategory> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM ToolCategory c WHERE c.tenantId = :tenantId AND LOWER(c.name) = LOWER(:name)")
    Optional<ToolCategory> findByTenantIdAndNameIgnoreCase(@Param("tenantId") UUID tenantId, @Param("name") String name);
}
