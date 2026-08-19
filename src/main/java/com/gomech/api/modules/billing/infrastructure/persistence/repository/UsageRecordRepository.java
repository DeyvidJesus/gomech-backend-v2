package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    @Query("SELECT u FROM UsageRecord u WHERE u.tenantId = :tenantId AND u.dimension = :dimension AND :now >= u.periodStart AND :now < u.periodEnd")
    Optional<UsageRecord> findCurrentPeriodUsage(
            @Param("tenantId") UUID tenantId,
            @Param("dimension") String dimension,
            @Param("now") OffsetDateTime now
    );

    @Query("SELECT u FROM UsageRecord u WHERE u.tenantId = :tenantId AND :now >= u.periodStart AND :now < u.periodEnd")
    List<UsageRecord> findAllCurrentPeriodUsage(
            @Param("tenantId") UUID tenantId,
            @Param("now") OffsetDateTime now
    );
}
