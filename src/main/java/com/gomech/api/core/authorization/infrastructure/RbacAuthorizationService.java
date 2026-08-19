package com.gomech.api.core.authorization.infrastructure;

import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.application.AuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Motor de decisão de autorização baseado em Papéis e Permissões (RBAC / PBAC)
 * com aplicação estrita de isolamento de Tenant e Unidade.
 */
@Slf4j
@Primary
@Component
public class RbacAuthorizationService implements AuthorizationService {

    public static final String ROLE_OWNER = "Proprietário";
    public static final String ROLE_OWNER_EN = "OWNER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Override
    public AccessDecision authorize(ActorContext actor, AuthorizationRequest request) {
        if (actor == null || actor.userId() == null || actor.tenantId() == null) {
            return AccessDecision.deny("unauthenticated_or_untrusted_tenant");
        }

        if (request == null) {
            return AccessDecision.deny("invalid_authorization_request");
        }

        // 1. Verificação de Papel de Proprietário / Admin (Acesso total no escopo do Tenant)
        Set<String> roles = actor.roles();
        boolean isOwner = roles.stream().anyMatch(r ->
                r.equalsIgnoreCase(ROLE_OWNER) || r.equalsIgnoreCase(ROLE_OWNER_EN) || r.equalsIgnoreCase(ROLE_ADMIN)
        );
        if (isOwner) {
            return AccessDecision.allow("owner_full_access");
        }

        // 2. Verificação de Escopo de Unidade (Unit Scope Isolation)
        Map<String, String> attributes = request.attributes();
        if (attributes != null && (attributes.containsKey("unit_id") || attributes.containsKey("unitId"))) {
            String targetUnitId = attributes.containsKey("unit_id") ? attributes.get("unit_id") : attributes.get("unitId");
            if (targetUnitId != null && !targetUnitId.isBlank()) {
                if (actor.unit() != null && !actor.unit().id().toString().equalsIgnoreCase(targetUnitId)) {
                    log.warn("Tentativa de acesso cross-unit negada para o usuário {}. Unidade ativa: {}, Unidade solicitada: {}",
                            actor.userId(), actor.unit().id(), targetUnitId);
                    return AccessDecision.deny("cross_unit_access_denied");
                }
            }
        }

        // 3. Verificação de Permissão Explícita ou Composta (PBAC)
        Set<String> actorPermissions = actor.permissions();
        String action = request.action() != null ? request.action().trim() : "";
        String resource = request.resource() != null ? request.resource().trim() : "";

        // Caso A: O action já é um código de permissão direto (ex: IAM_USER_READ ou user:create)
        boolean hasDirect = actorPermissions.stream().anyMatch(p -> p.equalsIgnoreCase(action));
        if (hasDirect) {
            return AccessDecision.allow("rbac_direct_permission_granted");
        }

        // Caso B: Formação composta resource_action (ex: IAM_USER + READ -> IAM_USER_READ ou resource:action)
        if (!resource.isBlank() && !action.isBlank()) {
            String compositeCode1 = resource + "_" + action;
            String compositeCode2 = resource + ":" + action;
            String compositeCode3 = action + ":" + resource;
            boolean hasComposite = actorPermissions.stream()
                    .anyMatch(p -> p.equalsIgnoreCase(compositeCode1) || p.equalsIgnoreCase(compositeCode2) || p.equalsIgnoreCase(compositeCode3));
            if (hasComposite) {
                return AccessDecision.allow("rbac_composite_permission_granted");
            }
        }

        log.debug("Permissão negada para o usuário {} na ação '{}' sobre o recurso '{}'", actor.userId(), action, resource);
        return AccessDecision.deny("permission_denied: required permission not held by actor");
    }
}
