package com.gomech.api.core.entitlement.application;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementDecision;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.domain.QuotaDimension;

import java.util.UUID;

/**
 * Contrato central do Core para resolução e avaliação de elegibilidade (entitlements),
 * verificação de acesso a módulos e fiscalização de cotas de assinatura.
 */
public interface EntitlementService {

    /**
     * Resolve o snapshot de capacidades efetivas do ator, cruzando suas permissões
     * de identidade (IAM) com os limites e módulos do plano de assinatura ativo do Tenant.
     */
    EntitlementSnapshot resolve(ActorContext actor);

    /**
     * Verifica se o Tenant possui acesso habilitado a um módulo específico de negócio.
     */
    EntitlementDecision checkModuleAccess(UUID tenantId, String moduleCode);

    /**
     * Avalia se o Tenant possui cota disponível para consumir ou alocar uma quantidade de recurso.
     */
    QuotaDecision checkQuota(UUID tenantId, QuotaDimension dimension, long requestedIncrement);

    /**
     * Registra o consumo efetivo de uma quantidade de recurso na dimensão especificada.
     */
    void recordUsage(UUID tenantId, QuotaDimension dimension, long amount);

    /**
     * Retorna a visão completa de capacidades e limites concedidos ao Tenant pela assinatura.
     */
    EntitlementSnapshot getTenantEntitlements(UUID tenantId);
}
