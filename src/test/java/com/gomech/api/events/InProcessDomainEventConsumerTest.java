package com.gomech.api.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(InProcessDomainEventConsumerTest.TestConfig.class)
class InProcessDomainEventConsumerTest {

    @Configuration
    static class TestConfig {

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }

        @Bean
        WorkOrderProjectionConsumer workOrderProjectionConsumer(EventRecorder recorder) {
            return new WorkOrderProjectionConsumer(recorder);
        }

        @Bean
        InventoryPurchaseNotificationConsumer inventoryPurchaseNotificationConsumer(EventRecorder recorder) {
            return new InventoryPurchaseNotificationConsumer(recorder);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationEventPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    private EventRecorder recorder;

    /**
     * The recorder is a singleton in a Spring context that JUnit caches across every test method
     * in this class, so entries recorded by one test would otherwise still be visible to the next.
     * Resetting before each test (rather than after) keeps the isolation intact even when a
     * previous test fails partway through.
     */
    @BeforeEach
    void resetRecorder() {
        recorder.clear();
    }

    @Test
    void work_order_completed_event_is_consumed_in_process() {
        publisher.publishEvent(new WorkOrderCompleted("wo-123", "tenant-1"));

        assertEquals(List.of("work-order-projection:wo-123"), recorder.entries());
    }

    @Test
    void inventory_purchase_event_is_consumed_by_its_own_consumer() {
        publisher.publishEvent(new InventoryPurchaseRecorded("purchase-9", "tenant-1"));

        assertEquals(List.of("inventory-purchase-notification:purchase-9"), recorder.entries());
    }

    record WorkOrderCompleted(String workOrderId, String tenantId) {}

    record InventoryPurchaseRecorded(String purchaseId, String tenantId) {}

    static final class EventRecorder {
        private final List<String> entries = new ArrayList<>();

        void record(String entry) {
            entries.add(entry);
        }

        List<String> entries() {
            return List.copyOf(entries);
        }

        void clear() {
            entries.clear();
        }
    }

    static final class WorkOrderProjectionConsumer {
        private final EventRecorder recorder;

        WorkOrderProjectionConsumer(EventRecorder recorder) {
            this.recorder = recorder;
        }

        @EventListener
        void on(WorkOrderCompleted event) {
            recorder.record("work-order-projection:" + event.workOrderId());
        }
    }

    static final class InventoryPurchaseNotificationConsumer {
        private final EventRecorder recorder;

        InventoryPurchaseNotificationConsumer(EventRecorder recorder) {
            this.recorder = recorder;
        }

        @EventListener
        void on(InventoryPurchaseRecorded event) {
            recorder.record("inventory-purchase-notification:" + event.purchaseId());
        }
    }
}
