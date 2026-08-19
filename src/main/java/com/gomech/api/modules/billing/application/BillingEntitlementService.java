package com.gomech.api.modules.billing.application;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementDecision;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlanFeature;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementação corporativa do contrato Core EntitlementService baseada em planos de Billing,
 * assinaturas ativas e medição de consumo por cotas.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class BillingEntitlementService implements EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final UsageService usageService;

    @Override
    @Transactional(readOnly = true)
    public EntitlementSnapshot resolve(ActorContext actor) {
        if (actor == null || actor.tenantId() == null) {
            return new EntitlementSnapshot(Set.of(), Set.of(), "NONE", Set.of(), Map.of());
        }

        Subscription subscription = getOrProvisionSubscription(actor.tenantId());
        if (!SubscriptionStatus.isOperational(subscription.getStatus())) {
            log.warn("Assinatura inoperante ({}) para o tenant {}", subscription.getStatus(), actor.tenantId());
            return new EntitlementSnapshot(Set.of(), actor.roles(), subscription.getPlanCode(), Set.of(), Map.of());
        }

        BillingPlan plan = subscription.getPlan();
        Set<String> enabledModules = extractEnabledModules(plan);
        Map<String, Long> quotaLimits = extractQuotaLimits(plan);

        // Interseção: se um módulo está desabilitado no plano, removemos as permissões correspondentes daquele módulo
        Set<String> effectivePermissions = actor.permissions().stream()
                .filter(perm -> isPermissionAllowedByPlan(perm, enabledModules))
                .collect(Collectors.toSet());

        return new EntitlementSnapshot(
                effectivePermissions,
                actor.roles(),
                subscription.getPlanCode(),
                enabledModules,
                quotaLimits
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementDecision checkModuleAccess(UUID tenantId, String moduleCode) {
        if (tenantId == null || moduleCode == null || moduleCode.isBlank()) {
            return EntitlementDecision.deny(moduleCode, "invalid_tenant_or_module");
        }

        Subscription subscription = getOrProvisionSubscription(tenantId);
        if (!SubscriptionStatus.isOperational(subscription.getStatus())) {
            return EntitlementDecision.deny(moduleCode, "subscription_inactive: status is " + subscription.getStatus());
        }

        BillingPlan plan = subscription.getPlan();
        Set<String> enabledModules = extractEnabledModules(plan);

        String normalized = normalizeModuleCode(moduleCode);
        if (enabledModules.contains(normalized)) {
            return EntitlementDecision.allow(moduleCode, "module_included_in_plan: " + subscription.getPlanCode());
        }

        log.info("Acesso ao módulo '{}' bloqueado para o tenant {}: não incluso no plano {}",
                moduleCode, tenantId, subscription.getPlanCode());
        return EntitlementDecision.deny(moduleCode, "module_not_included_in_plan: " + subscription.getPlanCode());
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaDecision checkQuota(UUID tenantId, QuotaDimension dimension, long requestedIncrement) {
        if (tenantId == null || dimension == null) {
            return QuotaDecision.deny(dimension, 0, 0, "invalid_tenant_or_dimension");
        }

        Subscription subscription = getOrProvisionSubscription(tenantId);
        if (!SubscriptionStatus.isOperational(subscription.getStatus())) {
            return QuotaDecision.deny(dimension, 0, 0, "subscription_inactive: " + subscription.getStatus());
        }

        BillingPlan plan = subscription.getPlan();
        long limit = getPlanQuotaLimit(plan, dimension.name());
        long currentUsage = usageService.getCurrentUsage(tenantId, dimension);

        // -1 indica cota ilimitada
        if (limit == -1) {
            return QuotaDecision.allow(dimension, currentUsage, -1, "unlimited_quota");
        }

        if (currentUsage + requestedIncrement > limit) {
            log.warn("Limite de cota excedido para o tenant {} na dimensão {}: limite={}, atual={}, solicitado={}",
                    tenantId, dimension.name(), limit, currentUsage, requestedIncrement);
            return QuotaDecision.deny(dimension, currentUsage, limit,
                    String.format("quota_exceeded: limit=%d, current=%d, requested=%d", limit, currentUsage, requestedIncrement));
        }

        return QuotaDecision.allow(dimension, currentUsage, limit, "quota_available");
    }

    @Override
    @Transactional
    public void recordUsage(UUID tenantId, QuotaDimension dimension, long amount) {
        usageService.recordUsage(tenantId, null, dimension, amount);
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementSnapshot getTenantEntitlements(UUID tenantId) {
        Subscription subscription = getOrProvisionSubscription(tenantId);
        BillingPlan plan = subscription.getPlan();
        Set<String> enabledModules = extractEnabledModules(plan);
        Map<String, Long> quotaLimits = extractQuotaLimits(plan);

        return new EntitlementSnapshot(
                Set.of(),
                Set.of(),
                subscription.getPlanCode(),
                enabledModules,
                quotaLimits
        );
    }

    private Subscription getOrProvisionSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> subscriptionService.createDefaultTrialSubscription(tenantId));
    }

    private Set<String> extractEnabledModules(BillingPlan plan) {
        if (plan == null || plan.getFeatures() == null) {
            return Set.of();
        }
        return plan.getFeatures().stream()
                .filter(BillingPlanFeature::isEnabled)
                .map(BillingPlanFeature::getFeatureCode)
                .map(this::normalizeModuleCode)
                .collect(Collectors.toSet());
    }

    private Map<String, Long> extractQuotaLimits(BillingPlan plan) {
        if (plan == null || plan.getFeatures() == null) {
            return Map.of();
        }
        Map<String, Long> limits = new HashMap<>();
        for (BillingPlanFeature f : plan.getFeatures()) {
            limits.put(f.getFeatureCode(), f.getLimitValue());
        }
        return limits;
    }

    private long getPlanQuotaLimit(BillingPlan plan, String dimensionCode) {
        if (plan == null || plan.getFeatures() == null) {
            return 0L;
        }
        return plan.getFeatures().stream()
                .filter(f -> f.getFeatureCode().equalsIgnoreCase(dimensionCode))
                .map(BillingPlanFeature::getLimitValue)
                .findFirst()
                .orElse(0L);
    }

    private boolean isPermissionAllowedByPlan(String permissionCode, Set<String> enabledModules) {
        if (permissionCode == null) return false;
        String upper = permissionCode.toUpperCase();

        // Mapear prefixos de permissão para módulos correspondentes
        if (upper.startsWith("CRM_") && !enabledModules.contains("CRM")) return false;
        if (upper.startsWith("OPERATIONS_") && !enabledModules.contains("OPERATIONS")) return false;
        if (upper.startsWith("INVENTORY_") && !enabledModules.contains("INVENTORY")) return false;
        if (upper.startsWith("FINANCE_") && !enabledModules.contains("FINANCE")) return false;
        if (upper.startsWith("AI_") && !enabledModules.contains("AI")) return false;

        return true;
    }

    private String normalizeModuleCode(String code) {
        if (code == null) return "";
        String upper = code.toUpperCase().trim();
        if (upper.startsWith("MODULE_")) {
            return upper.substring(7);
        }
        return upper;
    }
}
