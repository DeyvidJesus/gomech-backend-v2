package com.gomech.api.core.authorization;

import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.infrastructure.RbacAuthorizationService;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RbacAuthorizationServiceTest {

    private RbacAuthorizationService authorizationService;
    private UUID tenantId;
    private UUID unitAId;
    private UUID unitBId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        authorizationService = new RbacAuthorizationService();
        tenantId = UUID.randomUUID();
        unitAId = UUID.randomUUID();
        unitBId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Proprietário deve possuir acesso total irrestrito (Owner bypass)")
    void shouldAllowOwnerAccessUnconditionally() {
        ActorContext ownerActor = new ActorContext(
                userId,
                tenantId,
                UnitReference.of(unitAId),
                Set.of("Proprietário"),
                Set.of()
        );

        AuthorizationRequest request = new AuthorizationRequest("DELETE", "IAM_USER", "123", Map.of());

        AccessDecision decision = authorizationService.authorize(ownerActor, request);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("owner_full_access");
    }

    @Test
    @DisplayName("Deve autorizar quando o ator possui a permissão direta requerida")
    void shouldAllowDirectPermissionMatch() {
        ActorContext mechanicActor = new ActorContext(
                userId,
                tenantId,
                UnitReference.of(unitAId),
                Set.of("Mecânico"),
                Set.of("OPERATIONS_ORDER_EXECUTE", "CRM_VEHICLE_READ")
        );

        AuthorizationRequest request = new AuthorizationRequest("OPERATIONS_ORDER_EXECUTE", null, null, Map.of());

        AccessDecision decision = authorizationService.authorize(mechanicActor, request);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("rbac_direct_permission_granted");
    }

    @Test
    @DisplayName("Deve autorizar quando o recurso e ação combinam com a permissão do ator (Composite PBAC)")
    void shouldAllowCompositePermissionMatch() {
        ActorContext managerActor = new ActorContext(
                userId,
                tenantId,
                UnitReference.of(unitAId),
                Set.of("Gerente"),
                Set.of("CRM_CUSTOMER_READ", "CRM_CUSTOMER_WRITE")
        );

        AuthorizationRequest request = new AuthorizationRequest("READ", "CRM_CUSTOMER", "cust-1", Map.of());

        AccessDecision decision = authorizationService.authorize(managerActor, request);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("rbac_composite_permission_granted");
    }

    @Test
    @DisplayName("Deve negar acesso quando a permissão necessária não está presente no ator")
    void shouldDenyWhenPermissionIsMissing() {
        ActorContext mechanicActor = new ActorContext(
                userId,
                tenantId,
                UnitReference.of(unitAId),
                Set.of("Mecânico"),
                Set.of("OPERATIONS_ORDER_READ")
        );

        AuthorizationRequest request = new AuthorizationRequest("FINANCE_TRANSACTION_WRITE", "FINANCE", null, Map.of());

        AccessDecision decision = authorizationService.authorize(mechanicActor, request);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("permission_denied");
    }

    @Test
    @DisplayName("Deve negar acesso cross-unit quando a unidade da requisição difere da unidade ativa do ator")
    void shouldDenyCrossUnitAccess() {
        ActorContext mechanicAtUnitA = new ActorContext(
                userId,
                tenantId,
                UnitReference.of(unitAId),
                Set.of("Mecânico"),
                Set.of("OPERATIONS_ORDER_EXECUTE")
        );

        // Requisição para executar ordem na Unidade B
        AuthorizationRequest crossUnitRequest = new AuthorizationRequest(
                "OPERATIONS_ORDER_EXECUTE",
                "OPERATIONS",
                "order-99",
                Map.of("unit_id", unitBId.toString())
        );

        AccessDecision decision = authorizationService.authorize(mechanicAtUnitA, crossUnitRequest);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("cross_unit_access_denied");
    }

    @Test
    @DisplayName("Deve negar imediatamente se o ator não estiver autenticado ou não tiver tenant confiável")
    void shouldDenyUnauthenticatedOrUntrustedActor() {
        ActorContext untrustedActor = new ActorContext(
                null,
                null,
                null,
                Set.of(),
                Set.of()
        );

        AuthorizationRequest request = new AuthorizationRequest("READ", "CRM", null, Map.of());

        AccessDecision decision = authorizationService.authorize(untrustedActor, request);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("unauthenticated_or_untrusted_tenant");
    }
}
