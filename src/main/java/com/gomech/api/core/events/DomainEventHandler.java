package com.gomech.api.core.events;

public interface DomainEventHandler<T extends DomainEvent> {

    Class<T> eventType();

    void handle(EventEnvelope<T> event);
}
