package com.gomech.api.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.events.DomainEventHandler;
import com.gomech.api.core.events.EventEnvelope;
import com.gomech.api.core.events.EventHandlerRegistry;
import com.gomech.api.core.events.EventMetadataFactory;
import com.gomech.api.core.events.SpringDomainEventBus;
import com.gomech.api.core.events.SpringDomainEventDispatcher;
import com.gomech.api.core.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the dispatch semantics ADR-008 states: the dispatcher routes an envelope to <em>all</em>
 * registered handlers for that payload type, and only to matching handlers.
 *
 * <p>Isolation between handlers is what makes "all handlers" true in practice. Without it, one badly
 * behaved consumer silently suppresses every consumer registered after it — and since handlers are
 * discovered by Spring, which consumer that is depends on bean ordering.
 */
@SpringJUnitConfig(DomainEventDispatchSemanticsTest.TestConfig.class)
class DomainEventDispatchSemanticsTest {

    @Configuration
    static class TestConfig {

        @Bean
        EventMetadataFactory eventMetadataFactory() {
            return new EventMetadataFactory();
        }

        @Bean
        EventHandlerRegistry eventHandlerRegistry(List<DomainEventHandler<? extends DomainEvent>> handlers) {
            return new EventHandlerRegistry(handlers);
        }

        @Bean
        SpringDomainEventDispatcher springDomainEventDispatcher(EventHandlerRegistry registry) {
            return new SpringDomainEventDispatcher(registry);
        }

        @Bean
        DomainEventBus domainEventBus(ApplicationEventPublisher publisher, EventMetadataFactory factory) {
            return new SpringDomainEventBus(publisher, factory);
        }

        @Bean
        Recorder recorder() {
            return new Recorder();
        }

        /** Registered first, and always throws. */
        @Bean
        DomainEventHandler<WorkOrderCompleted> failingHandler(Recorder recorder) {
            return handler(WorkOrderCompleted.class, envelope -> {
                recorder.record("failing-handler-invoked");
                throw new IllegalStateException("consumer is broken");
            });
        }

        /** Registered after the failing one, and must still receive the event. */
        @Bean
        DomainEventHandler<WorkOrderCompleted> survivingHandler(Recorder recorder) {
            return handler(WorkOrderCompleted.class, envelope ->
                recorder.record("surviving-handler:" + envelope.payload().workOrderId()));
        }

        @Bean
        DomainEventHandler<InventoryPurchaseRecorded> otherEventHandler(Recorder recorder) {
            return handler(InventoryPurchaseRecorded.class, envelope ->
                recorder.record("other-event-handler"));
        }

        private static <T extends DomainEvent> DomainEventHandler<T> handler(
            Class<T> type, java.util.function.Consumer<EventEnvelope<T>> body) {
            return new DomainEventHandler<>() {
                @Override
                public Class<T> eventType() {
                    return type;
                }

                @Override
                public void handle(EventEnvelope<T> event) {
                    body.accept(event);
                }
            };
        }
    }

    @Autowired
    private DomainEventBus domainEventBus;

    @Autowired
    private Recorder recorder;

    private ch.qos.logback.classic.Logger dispatcherLogger;
    private ListAppender<ILoggingEvent> emitted;

    @BeforeEach
    void setUp() {
        recorder.clear();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        dispatcherLogger = context.getLogger(SpringDomainEventDispatcher.class);
        emitted = new ListAppender<>();
        emitted.setContext(context);
        emitted.start();
        dispatcherLogger.addAppender(emitted);
    }

    @AfterEach
    void tearDown() {
        dispatcherLogger.detachAppender(emitted);
        emitted.stop();
        recorder.clear();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void a_failing_handler_does_not_stop_the_remaining_handlers() {
        domainEventBus.publish(new WorkOrderCompleted("wo-1"));

        assertTrue(recorder.entries().contains("failing-handler-invoked"));
        assertTrue(recorder.entries().contains("surviving-handler:wo-1"),
            "ADR-008 routes an envelope to all registered handlers; one broken consumer must not "
                + "suppress the others. Recorded: " + recorder.entries());
    }

    @Test
    void a_failing_handler_does_not_break_the_publisher() {
        assertDoesNotThrow(() -> domainEventBus.publish(new WorkOrderCompleted("wo-2")),
            "a consumer's failure is its own to handle and must not roll back the publisher (ADR-003)");
    }

    @Test
    void a_handler_failure_is_logged_with_enough_context_to_find_it() {
        domainEventBus.publish(new WorkOrderCompleted("wo-3"));

        List<ILoggingEvent> errors = emitted.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertEquals(1, errors.size(), "a swallowed consumer failure would be invisible");
        String message = errors.getFirst().getFormattedMessage();
        assertTrue(message.contains("WorkOrderCompleted"), message);
        assertTrue(errors.getFirst().getThrowableProxy() != null,
            "the cause must be attached, not just described");
    }

    @Test
    void every_handler_registered_for_a_type_receives_the_event() {
        domainEventBus.publish(new WorkOrderCompleted("wo-4"));

        assertEquals(2, recorder.entries().size(),
            "both handlers registered for this type must be invoked: " + recorder.entries());
    }

    @Test
    void handlers_registered_for_another_type_are_not_invoked() {
        domainEventBus.publish(new InventoryPurchaseRecorded("purchase-1"));

        assertEquals(List.of("other-event-handler"), recorder.entries(),
            "dispatch is by payload type; unrelated handlers must not see the event");
    }

    record WorkOrderCompleted(String workOrderId) implements DomainEvent {
    }

    record InventoryPurchaseRecorded(String purchaseId) implements DomainEvent {
    }

    static final class Recorder {
        private final List<String> entries = new ArrayList<>();

        synchronized void record(String entry) {
            entries.add(entry);
        }

        synchronized List<String> entries() {
            return List.copyOf(entries);
        }

        synchronized void clear() {
            entries.clear();
        }
    }
}
