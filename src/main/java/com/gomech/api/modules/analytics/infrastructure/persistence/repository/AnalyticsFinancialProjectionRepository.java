package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsFinancialProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsFinancialProjectionRepository extends JpaRepository<AnalyticsFinancialProjection, UUID> {

    Optional<AnalyticsFinancialProjection> findByTenantIdAndRecordIdAndRecordType(
            UUID tenantId, UUID recordId, String recordType);

    List<AnalyticsFinancialProjection> findAllByTenantIdAndOccurredAtBetween(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsFinancialProjection> findAllByTenantIdAndUnitIdAndOccurredAtBetween(
            UUID tenantId, UUID unitId, OffsetDateTime from, OffsetDateTime to);

    List<AnalyticsFinancialProjection> findAllByTenantIdAndStatusAndDueDateLessThanEqual(
            UUID tenantId, String status, LocalDate date);

    Page<AnalyticsFinancialProjection> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<AnalyticsFinancialProjection> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);
}
