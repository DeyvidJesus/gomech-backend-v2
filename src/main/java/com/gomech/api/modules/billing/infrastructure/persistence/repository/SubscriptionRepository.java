package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = {"plan", "plan.features"})
    Optional<Subscription> findByTenantId(UUID tenantId);

    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
