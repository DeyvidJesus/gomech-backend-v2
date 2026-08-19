package com.gomech.api.core.authorization.infrastructure;

import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.application.AuthorizationService;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.infrastructure.StaticEntitlementService;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1PlaceholderBehaviourTest {

    private final AuthorizationService authorization = new RbacAuthorizationService();
    private final EntitlementService entitlement = new StaticEntitlementService();

    @Test
    @DisplayName("Authorization evaluates actor permissions and roles")
    void authorization_evaluates_actor_permissions() {
        ActorContext actor = new ActorContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UnitReference.of(UUID.randomUUID()),
                Set.of("Mecânico"),
                Set.of("user:create", "user:read")
        );

        AccessDecision direct = authorization.authorize(
                actor, new AuthorizationRequest("user:create", "user", "1", Map.of()));
        assertTrue(direct.allowed());

        AccessDecision missing = authorization.authorize(
                actor, new AuthorizationRequest("FINANCE_PAY", "FINANCE", "1", Map.of()));
        assertFalse(missing.allowed());
    }

    @Test
    @DisplayName("Entitlement reports exactly what the actor carries")
    void entitlement_reports_exactly_what_the_actor_carries() {
        ActorContext actor = actor();

        EntitlementSnapshot snapshot = entitlement.resolve(actor);

        assertEquals(actor.permissions(), snapshot.permissions());
        assertEquals(actor.roles(), snapshot.scopes());
    }

    @Test
    @DisplayName("Entitlement invents nothing for an actor that carries nothing")
    void entitlement_invents_nothing_for_an_actor_that_carries_nothing() {
        ActorContext bare = new ActorContext(
            UUID.randomUUID(), UUID.randomUUID(), null, Set.of(), Set.of());

        EntitlementSnapshot snapshot = entitlement.resolve(bare);

        assertTrue(snapshot.permissions().isEmpty());
        assertTrue(snapshot.scopes().isEmpty());
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
