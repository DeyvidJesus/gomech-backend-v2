package com.gomech.api.core.logging;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Closes the loop the issue is about: the filter is the production producer of the value
 * {@code EventMetadataFactory} reads, and that value survives all the way into an event handler.
 */
@SpringJUnitConfig(CorrelationIdEventPropagationTest.TestConfig.class)
class CorrelationIdEventPropagationTest {

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

        @Bean
        DomainEventHandler<WorkOrderCompleted> workOrderHandler(Recorder recorder) {
            return new DomainEventHandler<>() {
                @Override
                public Class<WorkOrderCompleted> eventType() {
                    return WorkOrderCompleted.class;
                }

                @Override
                public void handle(EventEnvelope<WorkOrderCompleted> event) {
                    // What the handler sees on its own thread, not what the envelope says.
                    recorder.record(CorrelationId.current());
                }
            };
        }
    }

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Autowired
    private DomainEventBus domainEventBus;

    @Autowired
    private Recorder recorder;

    @AfterEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        CorrelationId.clear();
        recorder.clear();
    }

    @Test
    void event_published_during_a_request_carries_that_requests_correlation_id() throws Exception {
        List<EventEnvelope<WorkOrderCompleted>> published = new ArrayList<>();

        runRequest("order-flow-7", () ->
            published.add(domainEventBus.publish(new WorkOrderCompleted("wo-1"))));

        assertEquals("order-flow-7", published.getFirst().metadata().correlationId(),
            "event metadata must pick up the correlation id established by the filter");
        assertEquals(List.of("order-flow-7"), recorder.entries(),
            "the handler must run inside the same correlation id");
    }

    @Test
    void generated_correlation_id_reaches_event_metadata_when_none_was_supplied() throws Exception {
        List<EventEnvelope<WorkOrderCompleted>> published = new ArrayList<>();

        runRequest(null, () -> published.add(domainEventBus.publish(new WorkOrderCompleted("wo-2"))));

        String correlationId = published.getFirst().metadata().correlationId();
        assertNotNull(correlationId, "a request without an inbound id still correlates its events");
        assertEquals(List.of(correlationId), recorder.entries());
    }

    @Test
    void dispatch_restores_the_surrounding_correlation_id_and_leaves_nothing_behind() throws Exception {
        runRequest("outer-request", () -> {
            domainEventBus.publish(new WorkOrderCompleted("wo-3"));
            assertEquals("outer-request", CorrelationId.current(),
                "dispatching an event must not disturb the correlation id of the request");
        });

        assertNull(CorrelationId.current(), "the request's id must not survive the request");
    }

    @Test
    void events_published_outside_a_request_simply_carry_no_correlation_id() {
        EventEnvelope<WorkOrderCompleted> envelope = domainEventBus.publish(new WorkOrderCompleted("wo-4"));

        assertNull(envelope.metadata().correlationId(),
            "no request, no correlation id, and that must not be an error");
        assertEquals(1, recorder.entries().size());
        assertNull(recorder.entries().getFirst());
    }

    private void runRequest(String inboundCorrelationId, Runnable duringRequest) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/work-orders");
        if (inboundCorrelationId != null) {
            request.addHeader(CorrelationId.HEADER, inboundCorrelationId);
        }

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                duringRequest.run();
            }
        });
    }

    record WorkOrderCompleted(String workOrderId) implements DomainEvent {
    }

    static final class Recorder {
        private final List<String> entries = new ArrayList<>();

        void record(String correlationId) {
            entries.add(correlationId);
        }

        List<String> entries() {
            return new ArrayList<>(entries);
        }

        void clear() {
            entries.clear();
        }
    }
}
