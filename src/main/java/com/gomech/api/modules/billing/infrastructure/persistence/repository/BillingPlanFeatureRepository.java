package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPlanFeatureRepository extends JpaRepository<BillingPlanFeature, UUID> {

    List<BillingPlanFeature> findAllByPlanId(UUID planId);

    Optional<BillingPlanFeature> findByPlanIdAndFeatureCode(UUID planId, String featureCode);
}
