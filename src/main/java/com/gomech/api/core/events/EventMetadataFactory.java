package com.gomech.api.core.events;

import com.gomech.api.core.logging.CorrelationId;
import com.gomech.api.core.tenancy.TenantContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EventMetadataFactory {

    public EventMetadata create(String eventType) {
        return new EventMetadata(
            UUID.randomUUID(),
            eventType,
            Instant.now(),
            TenantContextHolder.getTenantId(),
            resolveUserId(),
            CorrelationId.current()
        );
    }

    private UUID resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
