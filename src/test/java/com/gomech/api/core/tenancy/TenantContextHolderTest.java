package com.gomech.api.core.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariant under test: a tenant the caller merely asked for must never stand in for, or
 * silently replace, a tenant that was proven.
 */
class TenantContextHolderTest {

    private final UUID authenticatedTenant = UUID.randomUUID();
    private final UUID requestedTenant = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void resetContext() {
        TenantContextHolder.clear();
        UnitContextHolder.clear();
    }

    @Test
    void requested_tenant_is_accepted_when_nothing_is_established() {
        assertTrue(TenantContextHolder.setRequestedTenant(requestedTenant));

        assertEquals(requestedTenant, TenantContextHolder.getTenantId());
        assertEquals(TenantSource.REQUESTED, TenantContextHolder.getSource());
        assertFalse(TenantContextHolder.isTrusted(), "a caller-provided tenant must not be trusted");
    }

    @Test
    void authenticated_tenant_replaces_a_requested_one() {
        TenantContextHolder.setRequestedTenant(requestedTenant);

        TenantContextHolder.setAuthenticatedTenant(authenticatedTenant);

        assertEquals(authenticatedTenant, TenantContextHolder.getTenantId());
        assertEquals(TenantSource.AUTHENTICATED, TenantContextHolder.getSource());
        assertTrue(TenantContextHolder.isTrusted());
    }

    @Test
    void requested_tenant_cannot_override_an_authenticated_one() {
        TenantContextHolder.setAuthenticatedTenant(authenticatedTenant);

        boolean accepted = TenantContextHolder.setRequestedTenant(requestedTenant);

        assertFalse(accepted, "a header must not be able to replace an authenticated tenant");
        assertEquals(authenticatedTenant, TenantContextHolder.getTenantId());
        assertEquals(TenantSource.AUTHENTICATED, TenantContextHolder.getSource());
    }

    @Test
    void requested_tenant_cannot_override_a_system_established_one() {
        TenantContextHolder.setTenantId(authenticatedTenant);

        assertFalse(TenantContextHolder.setRequestedTenant(requestedTenant));
        assertEquals(authenticatedTenant, TenantContextHolder.getTenantId());
        assertEquals(TenantSource.SYSTEM, TenantContextHolder.getSource());
        assertTrue(TenantContextHolder.isTrusted());
    }

    @Test
    void clear_removes_the_tenant_and_its_source() {
        TenantContextHolder.setAuthenticatedTenant(authenticatedTenant);

        TenantContextHolder.clear();

        assertNull(TenantContextHolder.getTenantId());
        assertNull(TenantContextHolder.getSource());
        assertFalse(TenantContextHolder.isTrusted());
    }

    @Test
    void unit_context_is_absent_until_established_and_clears_cleanly() {
        assertTrue(UnitContextHolder.getUnit().isEmpty(), "no unit scope is a normal state");

        UUID unitId = UUID.randomUUID();
        UnitContextHolder.setUnit(UnitReference.of(unitId));
        assertEquals(unitId, UnitContextHolder.getUnit().orElseThrow().id());

        UnitContextHolder.clear();
        assertTrue(UnitContextHolder.getUnit().isEmpty());
    }
}
