package com.gomech.api.modules.billing.application;

import com.gomech.api.core.events.TenantReactivatedEvent;
import com.gomech.api.core.events.TenantSuspendedEvent;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.events.SubscriptionStatusChangedEvent;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelinquencyService {

    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void markDelinquent(UUID tenantId, String reason) {
        log.warn("Marcando tenant {} como inadimplente: {}", tenantId, reason);

        Optional<Subscription> opt = subscriptionRepository.findByTenantId(tenantId);
        if (opt.isEmpty()) {
            log.error("Assinatura não encontrada para o tenant {}", tenantId);
            return;
        }

        Subscription sub = opt.get();
        SubscriptionStatus prevStatus = SubscriptionStatus.valueOf(sub.getStatus());

        if (prevStatus == SubscriptionStatus.PAST_DUE || prevStatus == SubscriptionStatus.CANCELED) {
            log.info("Tenant {} já está com status {}", tenantId, prevStatus);
            return;
        }

        sub.setStatus(SubscriptionStatus.PAST_DUE.name());
        sub.setDelinquentSince(OffsetDateTime.now());
        subscriptionRepository.save(sub);

        // Dispara eventos de mudança de status e suspensão de acesso
        eventPublisher.publishEvent(new SubscriptionStatusChangedEvent(
                tenantId, sub.getId(), sub.getPlanCode(), prevStatus, SubscriptionStatus.PAST_DUE, OffsetDateTime.now()));

        eventPublisher.publishEvent(new TenantSuspendedEvent(
                tenantId, reason, OffsetDateTime.now()));
    }

    @Transactional
    public void recoverDelinquency(UUID tenantId) {
        log.info("Regularizando inadimplência e reativando acesso do tenant {}", tenantId);

        Optional<Subscription> opt = subscriptionRepository.findByTenantId(tenantId);
        if (opt.isEmpty()) return;

        Subscription sub = opt.get();
        SubscriptionStatus prevStatus = SubscriptionStatus.valueOf(sub.getStatus());

        sub.setStatus(SubscriptionStatus.ACTIVE.name());
        sub.setDelinquentSince(null);
        subscriptionRepository.save(sub);

        eventPublisher.publishEvent(new SubscriptionStatusChangedEvent(
                tenantId, sub.getId(), sub.getPlanCode(), prevStatus, SubscriptionStatus.ACTIVE, OffsetDateTime.now()));

        eventPublisher.publishEvent(new TenantReactivatedEvent(
                tenantId, "Pagamento confirmado", OffsetDateTime.now()));
    }

    @Transactional
    public void cancelSubscription(UUID tenantId, String reason) {
        log.warn("Cancelando assinatura do tenant {}: {}", tenantId, reason);

        Optional<Subscription> opt = subscriptionRepository.findByTenantId(tenantId);
        if (opt.isEmpty()) return;

        Subscription sub = opt.get();
        SubscriptionStatus prevStatus = SubscriptionStatus.valueOf(sub.getStatus());

        sub.setStatus(SubscriptionStatus.CANCELED.name());
        sub.setCanceledAt(OffsetDateTime.now());
        subscriptionRepository.save(sub);

        eventPublisher.publishEvent(new SubscriptionStatusChangedEvent(
                tenantId, sub.getId(), sub.getPlanCode(), prevStatus, SubscriptionStatus.CANCELED, OffsetDateTime.now()));

        eventPublisher.publishEvent(new TenantSuspendedEvent(
                tenantId, "Assinatura Cancelada: " + reason, OffsetDateTime.now()));
    }
}
