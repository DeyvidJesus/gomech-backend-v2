package com.gomech.api.core.audit.domain;

import com.gomech.api.core.tenancy.UnitReference;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One recorded audit fact: who did what, to which resource, in which scope.
 *
 * <p>Every field is metadata core can establish on its own — actor, tenant, unit and correlation all
 * come from the request context. Nothing here describes a business rule, and nothing requires
 * reaching into a module's data to populate it.
 *
 * @param unit          the unit in scope, or null when the action was not unit scoped
 * @param correlationId ties the entry to the request's log lines and to every event it published
 */
public record AuditEntry(
    UUID id,
    UUID tenantId,
    UUID userId,
    UnitReference unit,
    String correlationId,
    String action,
    String resource,
    String resourceId,
    Instant occurredAt,
    Map<String, String> metadata
) {

    public AuditEntry {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
