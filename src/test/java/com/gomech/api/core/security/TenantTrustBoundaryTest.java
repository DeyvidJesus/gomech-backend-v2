package com.gomech.api.core.security;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.infrastructure.SecurityContextActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.TenantFilter;
import com.gomech.api.core.tenancy.TenantSource;
import com.gomech.api.core.tenancy.UnitContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the tenant trust boundary.
 *
 * <p>The invariant: a caller-supplied {@code X-Tenant-ID} may, at most, choose which tenant a login
 * attempt is evaluated against. It must never become an identity, never displace a tenant proven by
 * a token, and never survive the request.
 */
class TenantTrustBoundaryTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String BUSINESS_PATH = "/api/v1/users";

    private final JwtUtil jwtUtil = new JwtUtil();
    private final SecurityContextActorContextProvider actorContextProvider =
        new SecurityContextActorContextProvider();

    private JwtAuthenticationFilter jwtFilter;

    private final UUID userId = UUID.randomUUID();
    private final UUID authenticatedTenant = UUID.randomUUID();
    private final UUID attackerTenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 900_000L);
        jwtFilter = new JwtAuthenticationFilter(jwtUtil);
        resetContext();
    }

    @AfterEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        UnitContextHolder.clear();
    }

    // ---------------------------------------- unauthenticated request with X-Tenant-ID

    @Test
    void unauthenticated_login_request_may_select_a_tenant_but_it_is_never_trusted() throws Exception {
        Captured captured = run(headerTrusted(), LOGIN_PATH, null, attackerTenant);

        assertEquals(attackerTenant, captured.tenantId,
            "login must still resolve a tenant before the user is authenticated");
        assertEquals(TenantSource.REQUESTED, captured.source);
        assertNull(captured.actor, "a selected tenant does not make anyone an actor");
    }

    @Test
    void unauthenticated_request_to_a_business_endpoint_ignores_the_tenant_header() throws Exception {
        Captured captured = run(headerTrusted(), BUSINESS_PATH, null, attackerTenant);

        assertNull(captured.tenantId,
            "only the authentication endpoint that needs a pre-auth tenant may select one");
        assertNull(captured.source);
    }

    @Test
    void tenant_header_is_ignored_entirely_when_not_explicitly_enabled() throws Exception {
        Captured captured = run(headerNotTrusted(), LOGIN_PATH, null, attackerTenant);

        assertNull(captured.tenantId,
            "deployed profiles leave the header disabled, so it must establish nothing at all");
        assertNull(captured.source);
    }

    // ------------------------------------------ authenticated request with X-Tenant-ID

    @Test
    void authenticated_request_with_matching_tenant_header_keeps_the_authenticated_tenant() throws Exception {
        String token = token(authenticatedTenant);

        Captured captured = run(headerTrusted(), LOGIN_PATH, token, authenticatedTenant);

        assertEquals(authenticatedTenant, captured.tenantId);
        assertEquals(TenantSource.AUTHENTICATED, captured.source,
            "agreement with the header must not downgrade the tenant to a caller-provided one");
        assertEquals(authenticatedTenant, captured.actor.tenantId());
    }

    @Test
    void authenticated_request_with_conflicting_tenant_header_keeps_the_authenticated_tenant() throws Exception {
        String token = token(authenticatedTenant);

        Captured captured = run(headerTrusted(), LOGIN_PATH, token, attackerTenant);

        assertEquals(authenticatedTenant, captured.tenantId,
            "the token's tenant is authoritative; the header must not redirect the request");
        assertEquals(TenantSource.AUTHENTICATED, captured.source);
        assertEquals(authenticatedTenant, captured.actor.tenantId(),
            "the actor must never be attributed to the tenant the caller asked for");
    }

    @Test
    void conflicting_tenant_header_cannot_reach_the_actor_on_a_business_endpoint() throws Exception {
        String token = token(authenticatedTenant);

        Captured captured = run(headerTrusted(), BUSINESS_PATH, token, attackerTenant);

        assertEquals(authenticatedTenant, captured.tenantId);
        assertEquals(authenticatedTenant, captured.actor.tenantId());
    }

    // ------------------------------------------------------------------ missing tenant

    @Test
    void request_without_any_tenant_information_establishes_no_tenant() throws Exception {
        Captured captured = run(headerTrusted(), BUSINESS_PATH, null, null);

        assertNull(captured.tenantId, "an absent tenant is a representable state, not a default");
        assertNull(captured.source);
        assertNull(captured.actor);
    }

    @Test
    void authenticated_token_without_a_tenant_claim_yields_an_actor_without_a_tenant() throws Exception {
        String tokenWithoutTenant = jwtUtil.generateToken(userId, null, null, List.of(), List.of());

        Captured captured = run(headerTrusted(), BUSINESS_PATH, tokenWithoutTenant, null);

        assertNull(captured.tenantId);
        assertNull(captured.actor.tenantId(),
            "a token that proves no tenant must not be topped up from anywhere else");
    }

    // ---------------------------------------------------------------- context cleanup

    @Test
    void tenant_context_is_cleared_after_every_request_shape() throws Exception {
        run(headerTrusted(), LOGIN_PATH, null, attackerTenant);
        assertCleared();

        run(headerTrusted(), LOGIN_PATH, token(authenticatedTenant), attackerTenant);
        assertCleared();

        run(headerNotTrusted(), BUSINESS_PATH, null, null);
        assertCleared();
    }

    @Test
    void a_selected_tenant_does_not_leak_into_the_next_request_on_the_same_thread() throws Exception {
        run(headerTrusted(), LOGIN_PATH, null, attackerTenant);
        SecurityContextHolder.clearContext();

        Captured second = run(headerTrusted(), BUSINESS_PATH, null, null);

        assertNull(second.tenantId, "a tenant selected by a previous caller leaked onto this thread");
    }

    private void assertCleared() {
        assertNull(TenantContextHolder.getTenantId());
        assertNull(TenantContextHolder.getSource());
        assertTrue(UnitContextHolder.getUnit().isEmpty());
        SecurityContextHolder.clearContext();
    }

    private TenantFilter headerTrusted() {
        return new TenantFilter(true);
    }

    private TenantFilter headerNotTrusted() {
        return new TenantFilter(false);
    }

    private String token(UUID tenantId) {
        return jwtUtil.generateToken(userId, tenantId);
    }

    /** Drives one request through the real filter order and captures what the handler saw. */
    private Captured run(TenantFilter tenantFilter, String path, String bearerToken, UUID tenantHeader)
            throws ServletException, IOException {

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (bearerToken != null) {
            request.addHeader("Authorization", "Bearer " + bearerToken);
        }
        if (tenantHeader != null) {
            request.addHeader("X-Tenant-ID", tenantHeader.toString());
        }

        AtomicReference<Captured> captured = new AtomicReference<>();
        FilterChain handler = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured.set(new Captured(
                    TenantContextHolder.getTenantId(),
                    TenantContextHolder.getSource(),
                    actorContextProvider.currentActor().orElse(null)
                ));
            }
        };

        tenantFilter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
            jwtFilter.doFilter(req, res, handler));

        return captured.get();
    }

    private record Captured(UUID tenantId, TenantSource source, ActorContext actor) {
    }
}
