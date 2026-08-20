package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanceCategoryRepository extends JpaRepository<FinanceCategory, UUID> {

    Optional<FinanceCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FinanceCategory> findAllByTenantId(UUID tenantId);

    List<FinanceCategory> findAllByTenantIdAndType(UUID tenantId, TransactionType type);

    Optional<FinanceCategory> findByTenantIdAndName(UUID tenantId, String name);
}
