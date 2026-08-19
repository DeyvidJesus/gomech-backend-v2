package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.ChangePlanRequest;
import com.gomech.api.modules.billing.api.dto.PlanFeatureDto;
import com.gomech.api.modules.billing.api.dto.SubscriptionResponse;
import com.gomech.api.modules.billing.domain.PlanCode;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository billingPlanRepository;

    @Transactional
    public Subscription createDefaultTrialSubscription(UUID tenantId) {
        Optional<Subscription> existing = subscriptionRepository.findByTenantId(tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BillingPlan trialPlan = billingPlanRepository.findByCode(PlanCode.TRIAL)
                .orElseGet(() -> billingPlanRepository.findAllByActiveTrue().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("Nenhum plano de cobrança cadastrado")));

        OffsetDateTime now = OffsetDateTime.now();
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(trialPlan);
        subscription.setPlanCode(trialPlan.getCode());
        subscription.setPlanName(trialPlan.getName());
        subscription.setStatus(SubscriptionStatus.TRIALING.name());
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusDays(14));
        subscription.setTrialEndsAt(now.plusDays(14));
        subscription.setNextBillingDate(LocalDate.now().plusDays(14));

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Assinatura TRIAL provisionada com sucesso para o tenant {} (plano: {})", tenantId, trialPlan.getCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultTrialSubscription(tenantId));

        return toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findByTenantId(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId);
    }

    @Transactional
    public SubscriptionResponse changePlan(UUID tenantId, ChangePlanRequest request) {
        BillingPlan newPlan = billingPlanRepository.findByCode(request.planCode())
                .orElseThrow(() -> new IllegalArgumentException("Plano não encontrado com o código: " + request.planCode()));

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    Subscription s = new Subscription();
                    s.setTenantId(tenantId);
                    return s;
                });

        OffsetDateTime now = OffsetDateTime.now();
        subscription.setPlan(newPlan);
        subscription.setPlanCode(newPlan.getCode());
        subscription.setPlanName(newPlan.getName());
        subscription.setStatus(SubscriptionStatus.ACTIVE.name());
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(1));
        subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
        subscription.setCancelAtPeriodEnd(false);

        Subscription updated = subscriptionRepository.save(subscription);
        log.info("Plano do tenant {} alterado com sucesso para {}", tenantId, newPlan.getCode());
        return toResponse(updated);
    }

    public SubscriptionResponse toResponse(Subscription subscription) {
        List<PlanFeatureDto> features = subscription.getPlan() != null && subscription.getPlan().getFeatures() != null
                ? subscription.getPlan().getFeatures().stream()
                .map(f -> new PlanFeatureDto(f.getFeatureCode(), f.getLimitValue(), f.isEnabled(), f.getUnitOfMeasure()))
                .toList()
                : List.of();

        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                subscription.getPlanCode(),
                subscription.getPlanName(),
                subscription.getStatus(),
                subscription.getNextBillingDate(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                subscription.isCancelAtPeriodEnd(),
                features
        );
    }
}
