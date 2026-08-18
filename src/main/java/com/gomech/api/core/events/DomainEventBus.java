package com.gomech.api.core.events;

public interface DomainEventBus {

    <T extends DomainEvent> EventEnvelope<T> publish(T event);
}
