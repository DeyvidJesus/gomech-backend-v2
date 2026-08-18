package com.gomech.api.core.audit.infrastructure;

import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.audit.domain.AuditEntry;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.logging.CorrelationId;
import com.gomech.api.core.tenancy.UnitReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records audit entries by emitting them to a dedicated audit logger.
 *
 * <h2>Why the sink is the log and not a table</h2>
 *
 * <p>The V1 architecture does define a persistent destination: {@code audit_logs}, owned by core,
 * append-only, written asynchronously (DATABASE_DESIGN §1.4, BACKEND_ARCHITECTURE §8). That table is
 * keyed on entity state transitions — {@code old_state_json} and {@code new_state_json} — which is a
 * business-domain auditing policy: something has to decide which entities are audited and how their
 * before/after states are captured. {@link AuditRecordRequest} carries no such state, and that policy
 * is deliberately not being designed here.
 *
 * <p>What the foundation does owe callers is that a recorded entry is not silently dropped. So this
 * implementation is a real sink: every entry leaves the process on the {@code com.gomech.audit}
 * logger, with a name of its own so it can be routed, filtered or shipped independently of
 * application logs. Combined with the correlation id, an entry can be tied back to the exact request
 * that produced it.
 *
 * <p>When the persistent writer arrives it replaces this bean and callers do not change, because they
 * depend on {@link AuditRecorder} rather than on this class.
 */
@Component
public class LoggingAuditRecorder implements AuditRecorder {

    /** Dedicated logger name so audit output can be routed separately from application logs. */
    public static final String AUDIT_LOGGER = "com.gomech.audit";

    private static final Logger auditLog = LoggerFactory.getLogger(AUDIT_LOGGER);

    @Override
    public AuditEntry record(ActorContext actor, AuditRecordRequest request) {
        AuditEntry entry = new AuditEntry(
            UUID.randomUUID(),
            actor == null ? null : actor.tenantId(),
            actor == null ? null : actor.userId(),
            actor == null ? null : actor.unit(),
            CorrelationId.current(),
            request.action(),
            request.resource(),
            request.resourceId(),
            Instant.now(),
            request.metadata()
        );

        auditLog.info(
            "audit id={} action={} resource={} resourceId={} tenant={} user={} unit={} correlation={} at={} metadata={}",
            entry.id(),
            entry.action(),
            entry.resource(),
            entry.resourceId(),
            entry.tenantId(),
            entry.userId(),
            unitIdOf(entry.unit()),
            entry.correlationId(),
            entry.occurredAt(),
            format(entry.metadata())
        );

        return entry;
    }

    private UUID unitIdOf(UnitReference unit) {
        return unit == null ? null : unit.id();
    }

    /** Stable ordering so an emitted entry is diffable and greppable. */
    private String format(Map<String, String> metadata) {
        return metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(",", "[", "]"));
    }
}
