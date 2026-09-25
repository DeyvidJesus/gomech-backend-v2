package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsDailyKpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsDailyKpiSnapshotRepository extends JpaRepository<AnalyticsDailyKpiSnapshot, UUID> {

    Optional<AnalyticsDailyKpiSnapshot> findByTenantIdAndUnitIdAndDateAndDimensionAndMetricName(
            UUID tenantId, UUID unitId, LocalDate date, String dimension, String metricName);

    Optional<AnalyticsDailyKpiSnapshot> findByTenantIdAndUnitIdIsNullAndDateAndDimensionAndMetricName(
            UUID tenantId, LocalDate date, String dimension, String metricName);

    List<AnalyticsDailyKpiSnapshot> findAllByTenantIdAndDateBetween(
            UUID tenantId, LocalDate startDate, LocalDate endDate);

    List<AnalyticsDailyKpiSnapshot> findAllByTenantIdAndUnitIdAndDateBetween(
            UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate);

    List<AnalyticsDailyKpiSnapshot> findAllByTenantIdAndDimensionAndDateBetween(
            UUID tenantId, String dimension, LocalDate startDate, LocalDate endDate);

    List<AnalyticsDailyKpiSnapshot> findAllByTenantIdAndUnitIdAndDimensionAndDateBetween(
            UUID tenantId, UUID unitId, String dimension, LocalDate startDate, LocalDate endDate);
}
