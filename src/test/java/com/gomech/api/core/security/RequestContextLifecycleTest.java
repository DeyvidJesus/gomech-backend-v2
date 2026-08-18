package com.gomech.api.core.security;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.infrastructure.SecurityContextActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.TenantFilter;
import com.gomech.api.core.tenancy.TenantSource;
import com.gomech.api.core.tenancy.UnitContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the required flow end to end at the filter level:
 * HTTP request → authentication → trusted context → {@link ActorContext}.
 *
 * <p>The filters are driven directly with mock servlet objects rather than through a running
 * container, which keeps the test in the infrastructure-free unit lane while still covering the real
 * ordering: {@link TenantFilter} is outermost, {@link JwtAuthenticationFilter} runs inside it.
 */
class RequestContextLifecycleTest {

    /** Same value as the local profile default, so the signing key is realistic. */
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final JwtUtil jwtUtil = new JwtUtil();
    private final SecurityContextActorContextProvider actorContextProvider =
        new SecurityContextActorContextProvider();

    private TenantFilter tenantFilter;
    private JwtAuthenticationFilter jwtFilter;

    private final UUID userId = UUID.randomUUID();
    private final UUID authenticatedTenant = UUID.randomUUID();
    private final UUID requestedTenant = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 900_000L);
        tenantFilter = new TenantFilter(true);
        jwtFilter = new JwtAuthenticationFilter(jwtUtil);
        resetContext();
    }

    @AfterEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        UnitContextHolder.clear();
    }

    @Test
    void authenticated_request_establishes_a_trusted_actor_context() throws Exception {
        String token = jwtUtil.generateToken(
            userId, authenticatedTenant, unitId, List.of("OWNER"), List.of("users:create"));

        AtomicReference<ActorContext> seenByHandler = new AtomicReference<>();
        runRequest(request(token, null), captureActorContext(seenByHandler));

        ActorContext actor = seenByHandler.get();
        assertNotNull(actor, "an authenticated request must reach the handler with an actor context");
        assertEquals(userId, actor.userId());
        assertEquals(authenticatedTenant, actor.tenantId());
        assertEquals(unitId, actor.unit().id());
        assertEquals(java.util.Set.of("OWNER"), actor.roles());
        assertEquals(java.util.Set.of("users:create"), actor.permissions());
    }

    @Test
    void unauthenticated_request_reaches_the_handler_without_an_actor() throws Exception {
        AtomicReference<ActorContext> seenByHandler = new AtomicReference<>();

        runRequest(request(null, null), captureActorContext(seenByHandler));

        assertNull(seenByHandler.get(), "no token means no actor, and that is a valid request state");
    }

    @Test
    void tenant_header_selects_a_tenant_only_while_unauthenticated() throws Exception {
        AtomicReference<TenantSource> sourceInHandler = new AtomicReference<>();
        AtomicReference<UUID> tenantInHandler = new AtomicReference<>();

        runRequest(request(null, requestedTenant), (req, res) -> {
            tenantInHandler.set(TenantContextHolder.getTenantId());
            sourceInHandler.set(TenantContextHolder.getSource());
        });

        assertEquals(requestedTenant, tenantInHandler.get(),
            "login must still be able to resolve a tenant before authentication");
        assertEquals(TenantSource.REQUESTED, sourceInHandler.get());
    }

    @Test
    void tenant_header_cannot_override_the_authenticated_tenant() throws Exception {
        String token = jwtUtil.generateToken(userId, authenticatedTenant);

        AtomicReference<UUID> tenantInHandler = new AtomicReference<>();
        AtomicReference<TenantSource> sourceInHandler = new AtomicReference<>();
        AtomicReference<ActorContext> actorInHandler = new AtomicReference<>();

        runRequest(request(token, requestedTenant), (req, res) -> {
            tenantInHandler.set(TenantContextHolder.getTenantId());
            sourceInHandler.set(TenantContextHolder.getSource());
            actorInHandler.set(actorContextProvider.currentActor().orElse(null));
        });

        assertEquals(authenticatedTenant, tenantInHandler.get(),
            "the proven tenant must win over the one the caller asked for");
        assertEquals(TenantSource.AUTHENTICATED, sourceInHandler.get());
        assertEquals(authenticatedTenant, actorInHandler.get().tenantId());
    }

    @Test
    void invalid_token_leaves_the_request_unauthenticated_and_establishes_no_tenant() throws Exception {
        AtomicReference<ActorContext> actorInHandler = new AtomicReference<>();
        AtomicReference<UUID> tenantInHandler = new AtomicReference<>();

        runRequest(request("not-a-real-token", null), (req, res) -> {
            actorInHandler.set(actorContextProvider.currentActor().orElse(null));
            tenantInHandler.set(TenantContextHolder.getTenantId());
        });

        assertNull(actorInHandler.get());
        assertNull(tenantInHandler.get(), "a token that fails verification must establish nothing");
    }

    @Test
    void context_is_cleared_after_the_request_completes() throws Exception {
        String token = jwtUtil.generateToken(userId, authenticatedTenant, unitId, List.of(), List.of());

        runRequest(request(token, requestedTenant), (req, res) -> {
            assertNotNull(TenantContextHolder.getTenantId(), "context is expected during the request");
            assertTrue(UnitContextHolder.getUnit().isPresent());
        });

        assertContextIsEmpty();
    }

    @Test
    void context_is_cleared_even_when_the_request_fails() {
        String token = jwtUtil.generateToken(userId, authenticatedTenant, unitId, List.of(), List.of());

        assertThrows(IllegalStateException.class, () -> runRequest(request(token, null), (req, res) -> {
            throw new IllegalStateException("handler blew up");
        }));

        assertContextIsEmpty();
    }

    @Test
    void a_second_request_on_the_same_thread_sees_no_leftover_context() throws Exception {
        String token = jwtUtil.generateToken(userId, authenticatedTenant, unitId, List.of("OWNER"), List.of());
        runRequest(request(token, null), (req, res) -> { });
        SecurityContextHolder.clearContext();

        AtomicReference<UUID> tenantInSecondRequest = new AtomicReference<>();
        AtomicReference<Boolean> unitInSecondRequest = new AtomicReference<>();

        runRequest(request(null, null), (req, res) -> {
            tenantInSecondRequest.set(TenantContextHolder.getTenantId());
            unitInSecondRequest.set(UnitContextHolder.getUnit().isPresent());
        });

        assertNull(tenantInSecondRequest.get(), "tenant leaked from the previous request on this thread");
        assertFalse(unitInSecondRequest.get(), "unit leaked from the previous request on this thread");
    }

    private void assertContextIsEmpty() {
        assertNull(TenantContextHolder.getTenantId());
        assertNull(TenantContextHolder.getSource());
        assertTrue(UnitContextHolder.getUnit().isEmpty());
    }

    /** Runs the request through the real filter order: TenantFilter, then JwtAuthenticationFilter. */
    private void runRequest(MockHttpServletRequest request, Handler handler) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain handlerChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                handler.handle((MockHttpServletRequest) req, (MockHttpServletResponse) res);
            }
        };

        tenantFilter.doFilter(request, response, (req, res) ->
            jwtFilter.doFilter(req, res, handlerChain));
    }

    private MockHttpServletRequest request(String bearerToken, UUID tenantHeader) {
        return request(LOGIN_PATH, bearerToken, tenantHeader);
    }

    private MockHttpServletRequest request(String path, String bearerToken, UUID tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (bearerToken != null) {
            request.addHeader("Authorization", "Bearer " + bearerToken);
        }
        if (tenantHeader != null) {
            request.addHeader("X-Tenant-ID", tenantHeader.toString());
        }
        return request;
    }

    private Handler captureActorContext(AtomicReference<ActorContext> sink) {
        return (req, res) -> sink.set(actorContextProvider.currentActor().orElse(null));
    }

    @FunctionalInterface
    private interface Handler {
        void handle(MockHttpServletRequest request, MockHttpServletResponse response);
    }
}
