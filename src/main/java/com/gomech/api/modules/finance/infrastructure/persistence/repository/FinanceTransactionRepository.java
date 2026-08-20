package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, UUID> {

    Optional<FinanceTransaction> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<FinanceTransaction> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<FinanceTransaction> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId, Pageable pageable);

    Page<FinanceTransaction> findAllByTenantIdAndAccountId(UUID tenantId, UUID accountId, Pageable pageable);

    List<FinanceTransaction> findAllByTenantIdAndTransactionDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate);

    List<FinanceTransaction> findAllByTenantIdAndCompetenceDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate);

    List<FinanceTransaction> findAllByTenantIdAndUnitIdAndCompetenceDateBetween(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate);
}
