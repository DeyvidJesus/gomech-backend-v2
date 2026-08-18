package com.gomech.api.core.tenancy;

import java.util.UUID;

/**
 * Holds the tenant the current request is scoped to, together with where that tenant came from.
 *
 * <p>The source matters: a caller-provided tenant selection must never override a tenant proven by
 * authentication. That invariant is enforced here rather than left to filter ordering, so it holds
 * no matter which order the filters run in.
 */
public class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<TenantSource> SOURCE_CONTEXT = new ThreadLocal<>();

    /**
     * Establishes the tenant proven by a verified access token. Always wins over a caller-provided
     * selection.
     */
    public static void setAuthenticatedTenant(UUID tenantId) {
        set(tenantId, TenantSource.AUTHENTICATED);
    }

    /**
     * Establishes a tenant derived server-side, for example the tenant the onboarding flow has just
     * created. Trusted, because no caller input decided it.
     */
    public static void setTenantId(UUID tenantId) {
        set(tenantId, TenantSource.SYSTEM);
    }

    /**
     * Records a tenant the caller asked for, used to resolve public endpoints such as login.
     *
     * <p>Ignored when a trusted tenant is already in scope, so a request header can never silently
     * replace an authenticated identity.
     *
     * @return whether the requested tenant was accepted
     */
    public static boolean setRequestedTenant(UUID tenantId) {
        TenantSource current = SOURCE_CONTEXT.get();
        if (current != null && current.isTrusted()) {
            return false;
        }
        set(tenantId, TenantSource.REQUESTED);
        return true;
    }

    public static UUID getTenantId() {
        return TENANT_CONTEXT.get();
    }

    /** Where the current tenant came from, or null when no tenant is in scope. */
    public static TenantSource getSource() {
        return SOURCE_CONTEXT.get();
    }

    /** Whether the tenant in scope was proven rather than merely requested by the caller. */
    public static boolean isTrusted() {
        TenantSource source = SOURCE_CONTEXT.get();
        return source != null && source.isTrusted();
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
        SOURCE_CONTEXT.remove();
    }

    private static void set(UUID tenantId, TenantSource source) {
        if (tenantId == null) {
            clear();
            return;
        }
        TENANT_CONTEXT.set(tenantId);
        SOURCE_CONTEXT.set(source);
    }
}
