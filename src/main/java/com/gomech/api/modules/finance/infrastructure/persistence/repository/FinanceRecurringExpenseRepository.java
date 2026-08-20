package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceRecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanceRecurringExpenseRepository extends JpaRepository<FinanceRecurringExpense, UUID> {

    Optional<FinanceRecurringExpense> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FinanceRecurringExpense> findAllByTenantId(UUID tenantId);

    List<FinanceRecurringExpense> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<FinanceRecurringExpense> findAllByTenantIdAndIsActiveTrue(UUID tenantId);
}
