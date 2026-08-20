package com.gomech.api.modules.finance.infrastructure.persistence.repository;

import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, UUID> {

    Optional<FinanceAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FinanceAccount> findAllByTenantId(UUID tenantId);

    List<FinanceAccount> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<FinanceAccount> findAllByTenantIdAndIsActiveTrue(UUID tenantId);
}
