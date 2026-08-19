package com.gomech.api.modules.crm.infrastructure.persistence.repository;

import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndDocumentAndDeletedAtIsNull(UUID tenantId, String document);

    boolean existsByTenantIdAndDocumentAndDeletedAtIsNull(UUID tenantId, String document);

    boolean existsByTenantIdAndDocumentAndIdNotAndDeletedAtIsNull(UUID tenantId, String document, UUID id);
}
