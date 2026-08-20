package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findByGatewayChargeId(String gatewayChargeId);

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    Page<Payment> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<Payment> findAllBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
}
