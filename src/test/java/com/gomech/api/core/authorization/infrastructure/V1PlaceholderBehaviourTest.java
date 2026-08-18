package com.gomech.api.core.authorization.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.application.AuthorizationService;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.infrastructure.StaticEntitlementService;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behaviour of the V1 placeholders so it is stated, not assumed.
 *
 * <p>These tests deliberately assert that authorization allows everything. They are not endorsing
 * that: they exist so the day someone implements real policy, this file fails loudly and has to be
 * rewritten, rather than the placeholder quietly surviving behind passing tests.
 */
class V1PlaceholderBehaviourTest {

    private final AuthorizationService authorization = new AllowAllAuthorizationService();
    private final EntitlementService entitlement = new StaticEntitlementService();

    private ch.qos.logback.classic.Logger placeholderLogger;
    private ListAppender<ILoggingEvent> emitted;

    @BeforeEach
    void captureLog() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        placeholderLogger = context.getLogger(AllowAllAuthorizationService.class);
        emitted = new ListAppender<>();
        emitted.setContext(context);
        emitted.start();
        placeholderLogger.addAppender(emitted);
    }

    @AfterEach
    void releaseLog() {
        placeholderLogger.detachAppender(emitted);
        emitted.stop();
    }

    @Test
    void authorization_allows_every_action_for_every_actor() {
        ActorContext powerless = new ActorContext(
            UUID.randomUUID(), UUID.randomUUID(), null, Set.of(), Set.of());

        for (String action : Set.of("user:create", "finance:pay", "anything:at:all")) {
            AccessDecision decision = authorization.authorize(
                powerless, new AuthorizationRequest(action, "resource", "1", Map.of()));

            assertTrue(decision.allowed(),
                "the V1 placeholder allows everything, including '" + action
                    + "' for an actor with no permissions at all");
        }
    }

    @Test
    void an_allow_is_marked_so_it_cannot_be_mistaken_for_an_evaluated_decision() {
        AccessDecision decision = authorization.authorize(
            actor(), new AuthorizationRequest("user:create", "user", "1", Map.of()));

        assertEquals(AllowAllAuthorizationService.PLACEHOLDER_REASON, decision.reason(),
            "every placeholder decision must be traceable to the placeholder, not read as real policy");
    }

    @Test
    void startup_warns_that_authorization_is_not_enforced() {
        new AllowAllAuthorizationService().warnThatAuthorizationIsNotEnforced();

        assertEquals(1, emitted.list.size());
        ILoggingEvent event = emitted.list.getFirst();
        assertEquals(Level.WARN, event.getLevel(),
            "an environment running without authorization must say so out loud");
        assertTrue(event.getFormattedMessage().contains("Authorization is NOT enforced"),
            event.getFormattedMessage());
    }

    @Test
    void entitlement_reports_exactly_what_the_actor_carries() {
        ActorContext actor = actor();

        EntitlementSnapshot snapshot = entitlement.resolve(actor);

        assertEquals(actor.permissions(), snapshot.permissions(),
            "the placeholder passes the actor's permissions through unchanged");
        assertEquals(actor.roles(), snapshot.scopes());
    }

    @Test
    void entitlement_invents_nothing_for_an_actor_that_carries_nothing() {
        ActorContext bare = new ActorContext(
            UUID.randomUUID(), UUID.randomUUID(), null, Set.of(), Set.of());

        EntitlementSnapshot snapshot = entitlement.resolve(bare);

        assertTrue(snapshot.permissions().isEmpty(),
            "returning fabricated entitlements would be worse than returning what is known");
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
