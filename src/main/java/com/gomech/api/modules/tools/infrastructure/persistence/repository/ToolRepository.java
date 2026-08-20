package com.gomech.api.modules.tools.infrastructure.persistence.repository;

import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
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
public interface ToolRepository extends JpaRepository<Tool, UUID> {

    @Query("SELECT t FROM Tool t WHERE t.id = :id AND t.tenantId = :tenantId AND t.deletedAt IS NULL")
    Optional<Tool> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM Tool t WHERE t.tenantId = :tenantId AND t.assetTag = :assetTag AND t.deletedAt IS NULL")
    Optional<Tool> findByTenantIdAndAssetTag(@Param("tenantId") UUID tenantId, @Param("assetTag") String assetTag);

    @Query("SELECT t FROM Tool t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL " +
            "AND (:unitId IS NULL OR t.unitId = :unitId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId) " +
            "AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(t.assetTag) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(t.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Tool> findAllFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("unitId") UUID unitId,
            @Param("status") ToolStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT t FROM Tool t WHERE t.tenantId = :tenantId AND t.currentHolderUserId = :userId AND t.deletedAt IS NULL")
    List<Tool> findByTenantIdAndCurrentHolderUserId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query("SELECT t FROM Tool t WHERE t.tenantId = :tenantId AND t.unitId = :unitId AND t.status = 'AVAILABLE' AND t.deletedAt IS NULL")
    List<Tool> findAvailableByUnitId(@Param("tenantId") UUID tenantId, @Param("unitId") UUID unitId);
}
