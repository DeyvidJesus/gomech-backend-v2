package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.domain.PayableStatus;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinancePayable;
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
public interface FinancePayableRepository extends JpaRepository<FinancePayable, UUID> {

    Optional<FinancePayable> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FinancePayable> findByTenantIdAndSourceCorrelationId(UUID tenantId, String sourceCorrelationId);

    Optional<FinancePayable> findByTenantIdAndInventoryPurchaseId(UUID tenantId, UUID inventoryPurchaseId);

    Page<FinancePayable> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<FinancePayable> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);

    Page<FinancePayable> findAllByTenantIdAndStatus(UUID tenantId, PayableStatus status, Pageable pageable);

    Page<FinancePayable> findAllByTenantIdAndUnitIdAndStatus(UUID tenantId, UUID unitId, PayableStatus status, Pageable pageable);

    List<FinancePayable> findAllByTenantIdAndDueDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate);

    List<FinancePayable> findAllByTenantIdAndUnitIdAndDueDateBetween(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(p.amount - p.paidAmount), 0) FROM FinancePayable p WHERE p.tenantId = :tenantId AND p.status = 'PENDING'")
    BigDecimal sumPendingPayables(@Param("tenantId") UUID tenantId);
}
