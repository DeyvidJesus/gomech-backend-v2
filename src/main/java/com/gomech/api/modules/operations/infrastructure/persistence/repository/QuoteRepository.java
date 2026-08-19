package com.gomech.api.modules.operations.infrastructure.persistence.repository;

import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID>, JpaSpecificationExecutor<Quote> {

    @Query("SELECT q FROM Quote q LEFT JOIN FETCH q.items WHERE q.id = :id AND q.tenantId = :tenantId")
    Optional<Quote> findByIdWithItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Quote> findByIdAndTenantId(UUID id, UUID tenantId);
}
