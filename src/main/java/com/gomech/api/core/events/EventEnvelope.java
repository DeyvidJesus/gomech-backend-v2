package com.gomech.api.core.events;

public record EventEnvelope<T extends DomainEvent>(
    EventMetadata metadata,
    T payload
) {
}
