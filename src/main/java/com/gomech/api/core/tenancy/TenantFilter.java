package com.gomech.api.core.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Outermost request filter, and therefore the boundary where per-request context begins and ends.
 *
 * <h2>Tenant trust model</h2>
 *
 * <p>Tenant identity is authoritative only when it comes from a verified access token. The
 * authentication filter establishes it as {@link TenantSource#AUTHENTICATED}; the onboarding flow
 * establishes the tenant it has just created as {@link TenantSource#SYSTEM}. Both are trusted
 * because no caller input decided them, and {@code @TenantId} then scopes every ORM read to them.
 *
 * <p>The {@code X-Tenant-ID} header is a development affordance, not part of the production
 * authentication flow: no client sends it, and login resolves its tenant from the credentials the
 * user supplies. It exists so a tenant-scoped endpoint can be exercised by hand before the
 * tenant-aware login story is finished. Three things keep it from becoming an identity:
 *
 * <ol>
 *   <li>It is off unless {@code gomech.tenancy.trust-request-header} is explicitly enabled, which
 *       only the local profile does. Deployed environments never accept it.</li>
 *   <li>Even when enabled it is honoured only on the public authentication endpoints that need a
 *       tenant before anyone is authenticated. It is ignored everywhere else.</li>
 *   <li>It is recorded as {@link TenantSource#REQUESTED}, which {@link TenantContextHolder} refuses
 *       to let overwrite a trusted tenant, and which never reaches {@code ActorContext}.</li>
 * </ol>
 *
 * <p>So a caller-supplied tenant can, at most, choose which tenant a login attempt is evaluated
 * against — and that attempt still requires valid credentials belonging to that tenant. It can never
 * act as, or displace, an authenticated identity.
 *
 * <p>Because this filter runs first, its {@code finally} block is the last code to execute for the
 * request. It clears every context holder there unconditionally, so nothing leaks onto a pooled
 * request thread even if the header is disabled, or a downstream filter or handler throws.
 */
@Component
@Order(TenantFilter.ORDER)
public class TenantFilter extends OncePerRequestFilter {

    /**
     * Runs immediately inside {@code CorrelationIdFilter}, so anything logged while resolving the
     * tenant already carries a correlation id, while this filter remains outside authentication and
     * therefore stays the boundary that clears tenant and unit context.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * The only endpoints that may resolve a tenant before authentication. Login is tenant-scoped at
     * the ORM layer, so it needs one; registration creates its own tenant and does not.
     */
    private static final Set<String> TENANT_SELECTABLE_PATHS = Set.of("/api/v1/auth/login");

    private final boolean trustRequestHeader;

    public TenantFilter(
            @Value("${gomech.tenancy.trust-request-header:false}") boolean trustRequestHeader
    ) {
        this.trustRequestHeader = trustRequestHeader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            if (maySelectTenant(request)) {
                applyRequestedTenant(request.getHeader(TENANT_HEADER));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            UnitContextHolder.clear();
        }
    }

    private boolean maySelectTenant(HttpServletRequest request) {
        if (!trustRequestHeader) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return TENANT_SELECTABLE_PATHS.contains(path);
    }

    private void applyRequestedTenant(String tenantHeader) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return;
        }
        try {
            TenantContextHolder.setRequestedTenant(UUID.fromString(tenantHeader));
        } catch (IllegalArgumentException ignored) {
            log.debug("Ignoring malformed {} header", TENANT_HEADER);
        }
    }
}
