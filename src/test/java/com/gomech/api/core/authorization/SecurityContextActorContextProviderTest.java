package com.gomech.api.core.authorization;

import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.infrastructure.SecurityContextActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.UnitContextHolder;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the production creation of {@link ActorContext}: what an authenticated request produces,
 * what an unauthenticated one produces, and which parts of the request context are trusted enough
 * to end up on the actor.
 */
class SecurityContextActorContextProviderTest {

    private final ActorContextProvider provider = new SecurityContextActorContextProvider();

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        UnitContextHolder.clear();
    }

    @Test
    void unauthenticated_request_produces_no_actor() {
        assertTrue(provider.currentActor().isEmpty(),
            "an unauthenticated request must be representable, not a half-populated actor");
    }

    @Test
    void authenticated_request_produces_an_actor_with_tenant_unit_and_authorities() {
        authenticateAs(userId, List.of(
            new SimpleGrantedAuthority("ROLE_OWNER"),
            new SimpleGrantedAuthority("users:create"),
            new SimpleGrantedAuthority("users:read")
        ));
        TenantContextHolder.setAuthenticatedTenant(tenantId);
        UnitContextHolder.setUnit(UnitReference.of(unitId));

        ActorContext actor = provider.currentActor().orElseThrow();

        assertEquals(userId, actor.userId());
        assertEquals(tenantId, actor.tenantId());
        assertEquals(UnitReference.of(unitId), actor.unit());
        assertEquals(Set.of("OWNER"), actor.roles(), "ROLE_ prefixed authorities are roles");
        assertEquals(Set.of("users:create", "users:read"), actor.permissions());
    }

    @Test
    void actor_without_unit_scope_is_valid() {
        authenticateAs(userId, List.of());
        TenantContextHolder.setAuthenticatedTenant(tenantId);

        ActorContext actor = provider.currentActor().orElseThrow();

        assertNull(actor.unit(), "a request that is not unit scoped still yields an actor");
        assertTrue(actor.roles().isEmpty());
        assertTrue(actor.permissions().isEmpty());
    }

    @Test
    void caller_requested_tenant_never_reaches_the_actor() {
        authenticateAs(userId, List.of());
        TenantContextHolder.setRequestedTenant(tenantId);

        ActorContext actor = provider.currentActor().orElseThrow();

        assertNull(actor.tenantId(),
            "a tenant the caller selected is not proven, so it must not be presented as the actor's tenant");
    }

    @Test
    void principal_that_is_not_a_user_id_produces_no_actor() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));

        Optional<ActorContext> actor = provider.currentActor();

        assertTrue(actor.isEmpty());
    }

    private void authenticateAs(UUID id, List<SimpleGrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(id.toString(), null, authorities));
    }
}
