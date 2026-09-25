package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsToolProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsToolProjectionRepository extends JpaRepository<AnalyticsToolProjection, UUID> {

    List<AnalyticsToolProjection> findAllByTenantIdAndOccurredAtBetween(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsToolProjection> findAllByTenantIdAndUnitIdAndOccurredAtBetween(
            UUID tenantId, UUID unitId, OffsetDateTime from, OffsetDateTime to);

    Page<AnalyticsToolProjection> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<AnalyticsToolProjection> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);
}
