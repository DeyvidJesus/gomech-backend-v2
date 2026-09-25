package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsInventoryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsInventoryProjectionRepository extends JpaRepository<AnalyticsInventoryProjection, UUID> {

    Optional<AnalyticsInventoryProjection> findByTenantIdAndEventRefIdAndMovementType(
            UUID tenantId, UUID eventRefId, String movementType);

    List<AnalyticsInventoryProjection> findAllByTenantIdAndOccurredAtBetween(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsInventoryProjection> findAllByTenantIdAndUnitIdAndOccurredAtBetween(
            UUID tenantId, UUID unitId, OffsetDateTime from, OffsetDateTime to);

    Page<AnalyticsInventoryProjection> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<AnalyticsInventoryProjection> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);
}
