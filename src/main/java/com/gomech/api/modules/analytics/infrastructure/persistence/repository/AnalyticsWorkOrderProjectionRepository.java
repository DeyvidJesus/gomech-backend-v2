package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsWorkOrderProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsWorkOrderProjectionRepository extends JpaRepository<AnalyticsWorkOrderProjection, UUID> {

    Optional<AnalyticsWorkOrderProjection> findByTenantIdAndWorkOrderId(UUID tenantId, UUID workOrderId);

    List<AnalyticsWorkOrderProjection> findAllByTenantIdAndOpenedAtBetween(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsWorkOrderProjection> findAllByTenantIdAndUnitIdAndOpenedAtBetween(
            UUID tenantId, UUID unitId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsWorkOrderProjection> findAllByTenantIdAndCompletedAtBetween(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsWorkOrderProjection> findAllByTenantIdAndUnitIdAndCompletedAtBetween(
            UUID tenantId, UUID unitId, OffsetDateTime from, OffsetDateTime to);

    Page<AnalyticsWorkOrderProjection> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<AnalyticsWorkOrderProjection> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);
}
