package com.gomech.api.core.entitlement.infrastructure;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementDecision;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementação estática de fallback do contrato EntitlementService.
 * Utilizada quando nenhum módulo de Billing específico estiver ativo ou em testes isolados do Core.
 */
@Component
public class StaticEntitlementService implements EntitlementService {

    @Override
    public EntitlementSnapshot resolve(ActorContext actor) {
        if (actor == null) {
            return new EntitlementSnapshot(Set.of(), Set.of(), "DEFAULT", Set.of(), Map.of());
        }
        return new EntitlementSnapshot(actor.permissions(), actor.roles(), "DEFAULT", Set.of(), Map.of());
    }

    @Override
    public EntitlementDecision checkModuleAccess(UUID tenantId, String moduleCode) {
        return EntitlementDecision.allow(moduleCode, "static_fallback_allowed");
    }

    @Override
    public QuotaDecision checkQuota(UUID tenantId, QuotaDimension dimension, long requestedIncrement) {
        return QuotaDecision.allow(dimension, 0, -1, "static_fallback_unlimited");
    }

    @Override
    public void recordUsage(UUID tenantId, QuotaDimension dimension, long amount) {
        // No-op para static fallback
    }

    @Override
    public EntitlementSnapshot getTenantEntitlements(UUID tenantId) {
        return new EntitlementSnapshot(Set.of(), Set.of(), "DEFAULT", Set.of(), Map.of());
    }
}
