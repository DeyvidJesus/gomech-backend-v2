package com.gomech.api.core;

import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.audit.domain.AuditEntry;
import com.gomech.api.core.audit.infrastructure.LoggingAuditRecorder;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.application.AuthorizationService;
import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.infrastructure.RbacAuthorizationService;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.infrastructure.StaticEntitlementService;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreContractsTest {

    @Test
    void authorization_contract_evaluates_rbac_actor() {
        AuthorizationService service = new RbacAuthorizationService();
        ActorContext actor = actor();

        AccessDecision decision = service.authorize(
            actor,
            new AuthorizationRequest("user:read", "user", "123", Map.of())
        );

        assertTrue(decision.allowed());
    }

    @Test
    void entitlement_contract_returns_actor_snapshot() {
        EntitlementService service = new StaticEntitlementService();
        ActorContext actor = actor();

        EntitlementSnapshot snapshot = service.resolve(actor);

        assertEquals(actor.permissions(), snapshot.permissions());
        assertEquals(actor.roles(), snapshot.scopes());
    }

    /**
     * Contract-level check that the recorder returns a fully populated entry. That the entry is
     * also actually emitted, rather than built and dropped, is covered by
     * {@code LoggingAuditRecorderTest}.
     */
    @Test
    void audit_contract_preserves_actor_and_request_metadata() {
        AuditRecorder recorder = new LoggingAuditRecorder();
        ActorContext actor = actor();

        AuditEntry entry = recorder.record(
            actor,
            new AuditRecordRequest(
                "user.created",
                "user",
                "123",
                Map.of("correlationId", "corr-1", "source", "contract-test")
            )
        );

        assertNotNull(entry.id());
        assertNotNull(entry.occurredAt());
        assertEquals(actor.userId(), entry.userId());
        assertEquals(actor.tenantId(), entry.tenantId());
        assertEquals("user.created", entry.action());
        assertEquals("user", entry.resource());
        assertEquals("123", entry.resourceId());
        assertEquals("corr-1", entry.metadata().get("correlationId"));
    }

    private ActorContext actor() {
        return new ActorContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UnitReference.of(UUID.randomUUID()),
            Set.of("OWNER"),
            Set.of("user:create", "user:read")
        );
    }
}
