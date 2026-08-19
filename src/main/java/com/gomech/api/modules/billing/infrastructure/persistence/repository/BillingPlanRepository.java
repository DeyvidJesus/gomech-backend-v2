package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    @EntityGraph(attributePaths = {"features"})
    Optional<BillingPlan> findByCode(String code);

    @EntityGraph(attributePaths = {"features"})
    List<BillingPlan> findAllByActiveTrue();

    @Override
    @EntityGraph(attributePaths = {"features"})
    List<BillingPlan> findAll();
}
