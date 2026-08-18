package com.gomech.api.events;

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
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(DomainEventBusDispatchTest.TestConfig.class)
class DomainEventBusDispatchTest {

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
        SpringDomainEventDispatcher springDomainEventDispatcher(EventHandlerRegistry eventHandlerRegistry) {
            return new SpringDomainEventDispatcher(eventHandlerRegistry);
        }

        @Bean
        DomainEventBus domainEventBus(
            org.springframework.context.ApplicationEventPublisher publisher,
            EventMetadataFactory eventMetadataFactory
        ) {
            return new SpringDomainEventBus(publisher, eventMetadataFactory);
        }

        @Bean
        HandlerRecorder handlerRecorder() {
            return new HandlerRecorder();
        }

        @Bean
        DomainEventHandler<WorkOrderCompleted> workOrderProjectionHandler(HandlerRecorder recorder) {
            return new DomainEventHandler<>() {
                @Override
                public Class<WorkOrderCompleted> eventType() {
                    return WorkOrderCompleted.class;
                }

                @Override
                public void handle(EventEnvelope<WorkOrderCompleted> event) {
                    recorder.record(event);
                }
            };
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private DomainEventBus domainEventBus;

    @org.springframework.beans.factory.annotation.Autowired
    private HandlerRecorder handlerRecorder;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        MDC.clear();
        handlerRecorder.clear();
    }

    @Test
    void registered_handler_receives_typed_event_with_metadata() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of())
        );
        MDC.put("correlation_id", "corr-123");

        EventEnvelope<WorkOrderCompleted> published = domainEventBus.publish(new WorkOrderCompleted("wo-1"));

        assertEquals(1, handlerRecorder.events().size());

        EventEnvelope<WorkOrderCompleted> handled = handlerRecorder.events().getFirst();
        assertEquals("wo-1", handled.payload().workOrderId());
        assertEquals(tenantId, handled.metadata().tenantId());
        assertEquals(userId, handled.metadata().userId());
        assertEquals("corr-123", handled.metadata().correlationId());
        assertEquals("WorkOrderCompleted", handled.metadata().eventType());
        assertNotNull(handled.metadata().eventId());
        assertNotNull(handled.metadata().occurredAt());
        assertEquals(published, handled);
    }

    @Test
    void dispatch_ignores_unregistered_event_types() {
        domainEventBus.publish(new InventoryPurchaseRecorded("purchase-1"));

        assertEquals(0, handlerRecorder.events().size());
    }

    record WorkOrderCompleted(String workOrderId) implements DomainEvent {
    }

    record InventoryPurchaseRecorded(String purchaseId) implements DomainEvent {
    }

    static final class HandlerRecorder {
        private final List<EventEnvelope<WorkOrderCompleted>> events = new ArrayList<>();

        void record(EventEnvelope<WorkOrderCompleted> event) {
            events.add(event);
        }

        List<EventEnvelope<WorkOrderCompleted>> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
