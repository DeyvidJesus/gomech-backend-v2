package com.gomech.api.core.tenancy;

/**
 * Where the tenant currently in scope came from, which decides whether it may be trusted.
 *
 * <p>The distinction exists because one public endpoint genuinely needs a tenant before anyone is
 * authenticated: login resolves a user inside a tenant, and the user entity is tenant-filtered. That
 * selection is caller-provided and therefore untrusted. It must never be able to stand in for, or
 * quietly replace, the tenant proven by a verified token.
 */
public enum TenantSource {

    /** Proven by a verified access token. Trusted. */
    AUTHENTICATED,

    /** Established server-side, for example by the onboarding flow that just created the tenant. Trusted. */
    SYSTEM,

    /** Selected by the caller, for example through a request header on a public endpoint. Not trusted. */
    REQUESTED;

    public boolean isTrusted() {
        return this != REQUESTED;
    }
}
