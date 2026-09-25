package com.gomech.api.modules.ai.infrastructure.persistence.repository;

import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiGatewayAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiGatewayAuditLogRepository extends JpaRepository<AiGatewayAuditLog, UUID> {

    Page<AiGatewayAuditLog> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<AiGatewayAuditLog> findAllByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime from, OffsetDateTime to);
}
