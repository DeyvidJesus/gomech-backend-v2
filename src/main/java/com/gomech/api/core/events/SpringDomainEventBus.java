package com.gomech.api.core.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringDomainEventBus implements DomainEventBus {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventMetadataFactory eventMetadataFactory;

    public SpringDomainEventBus(
        ApplicationEventPublisher applicationEventPublisher,
        EventMetadataFactory eventMetadataFactory
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.eventMetadataFactory = eventMetadataFactory;
    }

    @Override
    public <T extends DomainEvent> EventEnvelope<T> publish(T event) {
        EventEnvelope<T> envelope = new EventEnvelope<>(
            eventMetadataFactory.create(event.eventType()),
            event
        );
        applicationEventPublisher.publishEvent(envelope);
        return envelope;
    }
}
