package com.gomech.api.modules.ai.infrastructure.persistence.repository;

import com.gomech.api.modules.ai.domain.AiActionProposalStatus;
import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiActionProposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiActionProposalRepository extends JpaRepository<AiActionProposal, UUID> {

    Optional<AiActionProposal> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AiActionProposal> findAllByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, AiActionProposalStatus status, Pageable pageable);

    @Query("SELECT p FROM AiActionProposal p WHERE p.tenantId = :tenantId " +
           "AND (:unitId IS NULL OR p.unitId = :unitId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    Page<AiActionProposal> searchProposals(
            @Param("tenantId") UUID tenantId,
            @Param("unitId") UUID unitId,
            @Param("status") AiActionProposalStatus status,
            Pageable pageable);
}
