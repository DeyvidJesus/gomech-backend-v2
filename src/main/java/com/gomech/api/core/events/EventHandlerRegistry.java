package com.gomech.api.core.events;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EventHandlerRegistry {

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> handlersByType;

    public EventHandlerRegistry(List<DomainEventHandler<? extends DomainEvent>> handlers) {
        this.handlersByType = handlers.stream()
            .collect(Collectors.groupingBy(DomainEventHandler::eventType));
    }

    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<DomainEventHandler<T>> handlersFor(Class<T> eventType) {
        return handlersByType.getOrDefault(eventType, Collections.emptyList())
            .stream()
            .map(handler -> (DomainEventHandler<T>) handler)
            .toList();
    }
}
