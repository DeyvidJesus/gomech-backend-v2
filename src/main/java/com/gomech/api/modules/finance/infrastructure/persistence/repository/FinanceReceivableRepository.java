package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.domain.ReceivableStatus;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceReceivable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanceReceivableRepository extends JpaRepository<FinanceReceivable, UUID> {

    Optional<FinanceReceivable> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FinanceReceivable> findByTenantIdAndSourceCorrelationId(UUID tenantId, String sourceCorrelationId);

    Optional<FinanceReceivable> findByTenantIdAndWorkOrderId(UUID tenantId, UUID workOrderId);

    Page<FinanceReceivable> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<FinanceReceivable> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);

    Page<FinanceReceivable> findAllByTenantIdAndStatus(UUID tenantId, ReceivableStatus status, Pageable pageable);

    Page<FinanceReceivable> findAllByTenantIdAndUnitIdAndStatus(UUID tenantId, UUID unitId, ReceivableStatus status, Pageable pageable);

    List<FinanceReceivable> findAllByTenantIdAndDueDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate);

    List<FinanceReceivable> findAllByTenantIdAndUnitIdAndDueDateBetween(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(r.amount - r.paidAmount), 0) FROM FinanceReceivable r WHERE r.tenantId = :tenantId AND r.status = 'PENDING'")
    BigDecimal sumPendingReceivables(@Param("tenantId") UUID tenantId);
}
