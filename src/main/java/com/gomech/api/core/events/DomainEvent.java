package com.gomech.api.core.events;

public interface DomainEvent {

    default String eventType() {
        return getClass().getSimpleName();
    }
}
