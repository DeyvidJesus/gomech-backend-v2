package com.gomech.api.core.events;

import com.gomech.api.core.logging.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Routes a published envelope to the handlers registered for its payload type, in process.
 *
 * <h2>Dispatch semantics</h2>
 *
 * <p>Per ADR-008 the envelope goes to <em>all</em> registered handlers for that payload type, and
 * only to handlers that declared exactly that type. Matching is on the payload's runtime class, so a
 * handler declaring a supertype does not receive subtypes: contracts stay explicit and
 * compiler-checked, which is the point of typed registration.
 *
 * <h2>Failure semantics</h2>
 *
 * <p>Each handler is isolated. A handler that throws is logged with the event type and the handler
 * that failed, and dispatch continues to the remaining handlers. Two reasons, both from the ADRs:
 *
 * <ul>
 *   <li>ADR-008 says every registered handler receives the event. Without isolation, one broken
 *       consumer silently suppresses every consumer after it, and which ones those are depends on
 *       Spring's bean ordering.</li>
 *   <li>ADR-003 says a consumer reacts to a business fact that has already happened and must not
 *       roll back the publisher, and that failures in listeners require deliberate handling by the
 *       consumer that owns the consequence.</li>
 * </ul>
 *
 * <p>So a failure here is neither hidden nor propagated: it is reported at ERROR with its cause
 * attached, under the publishing request's correlation id. A consumer that needs retries, a dead
 * letter, or compensation owns that behaviour itself.
 */
@Component
public class SpringDomainEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventDispatcher.class);

    private final EventHandlerRegistry eventHandlerRegistry;

    public SpringDomainEventDispatcher(EventHandlerRegistry eventHandlerRegistry) {
        this.eventHandlerRegistry = eventHandlerRegistry;
    }

    /**
     * Handlers run inside the correlation id the event was published under, taken from the envelope
     * rather than from whatever happens to be on the thread. On the request thread the two are the
     * same; sourcing it from the envelope is what keeps handler logs correlated if an event is ever
     * dispatched from somewhere else. The previous value is restored afterwards, so dispatch never
     * disturbs the surrounding scope.
     */
    @EventListener
    public void dispatch(EventEnvelope<? extends DomainEvent> envelope) {
        try (CorrelationId.Scope ignored = CorrelationId.scope(envelope.metadata().correlationId())) {
            dispatchTyped(envelope);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> void dispatchTyped(EventEnvelope<? extends DomainEvent> envelope) {
        EventEnvelope<T> typedEnvelope = (EventEnvelope<T>) envelope;
        Class<T> eventType = (Class<T>) typedEnvelope.payload().getClass();

        for (DomainEventHandler<T> handler : eventHandlerRegistry.handlersFor(eventType)) {
            invoke(handler, typedEnvelope, eventType);
        }
    }

    private <T extends DomainEvent> void invoke(
        DomainEventHandler<T> handler,
        EventEnvelope<T> envelope,
        Class<T> eventType
    ) {
        try {
            handler.handle(envelope);
        } catch (RuntimeException e) {
            log.error(
                "Domain event handler {} failed for {} (eventId={}); remaining handlers still run",
                handler.getClass().getName(),
                eventType.getSimpleName(),
                envelope.metadata().eventId(),
                e
            );
        }
    }
}
