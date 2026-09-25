package com.gomech.api.modules.analytics.infrastructure.persistence.repository;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsProcessedEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnalyticsProcessedEventLogRepository extends JpaRepository<AnalyticsProcessedEventLog, UUID> {

    boolean existsByTenantIdAndEventId(UUID tenantId, String eventId);
}
