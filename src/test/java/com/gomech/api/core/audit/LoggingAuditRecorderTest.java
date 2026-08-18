package com.gomech.api.core.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.audit.domain.AuditEntry;
import com.gomech.api.core.audit.infrastructure.LoggingAuditRecorder;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.logging.CorrelationId;
import com.gomech.api.core.tenancy.UnitReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of these tests is that the recorder is a <em>sink</em>: an entry must actually leave the
 * process, not merely be constructed and returned. So the assertions read the emitted log event
 * rather than only the returned value — the previous implementation would have passed a
 * return-value-only test while dropping every record.
 */
class LoggingAuditRecorderTest {

    private final AuditRecorder recorder = new LoggingAuditRecorder();

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    private ch.qos.logback.classic.Logger auditLogger;
    private ListAppender<ILoggingEvent> emitted;

    @BeforeEach
    void captureAuditLog() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        auditLogger = context.getLogger(LoggingAuditRecorder.AUDIT_LOGGER);
        emitted = new ListAppender<>();
        emitted.setContext(context);
        emitted.start();
        auditLogger.addAppender(emitted);
        auditLogger.setLevel(Level.INFO);
        CorrelationId.clear();
    }

    @AfterEach
    void releaseAuditLog() {
        auditLogger.detachAppender(emitted);
        emitted.stop();
        CorrelationId.clear();
    }

    @Test
    void recorded_entry_is_actually_emitted() {
        recorder.record(actor(), request());

        assertEquals(1, emitted.list.size(), "a recorded entry must leave the process, not be discarded");
        assertEquals(Level.INFO, emitted.list.getFirst().getLevel());
    }

    @Test
    void entry_carries_the_actor_tenant_unit_and_correlation_metadata_core_can_establish() {
        try (CorrelationId.Scope ignored = CorrelationId.scope("req-42")) {
            AuditEntry entry = recorder.record(actor(), request());

            assertNotNull(entry.id());
            assertNotNull(entry.occurredAt());
            assertEquals(tenantId, entry.tenantId());
            assertEquals(userId, entry.userId());
            assertEquals(UnitReference.of(unitId), entry.unit());
            assertEquals("req-42", entry.correlationId());
            assertEquals("user.created", entry.action());
            assertEquals("user", entry.resource());
            assertEquals("123", entry.resourceId());
            assertEquals("contract-test", entry.metadata().get("source"));
        }
    }

    @Test
    void emitted_line_contains_the_scope_needed_to_trace_the_entry() {
        try (CorrelationId.Scope ignored = CorrelationId.scope("req-77")) {
            recorder.record(actor(), request());
        }

        String line = emitted.list.getFirst().getFormattedMessage();
        assertTrue(line.contains("action=user.created"), line);
        assertTrue(line.contains("resource=user"), line);
        assertTrue(line.contains("tenant=" + tenantId), line);
        assertTrue(line.contains("user=" + userId), line);
        assertTrue(line.contains("unit=" + unitId), line);
        assertTrue(line.contains("correlation=req-77"), line);
        assertTrue(line.contains("source=contract-test"), line);
    }

    @Test
    void entry_outside_a_request_records_what_is_known_and_omits_what_is_not() {
        AuditEntry entry = recorder.record(actor(), request());

        assertNull(entry.correlationId(), "no request means no correlation id, and that is not an error");
        assertEquals(1, emitted.list.size(), "the entry is still emitted");
    }

    @Test
    void action_without_unit_scope_is_recorded_without_a_unit() {
        ActorContext actorWithoutUnit =
            new ActorContext(userId, tenantId, null, Set.of(), Set.of());

        AuditEntry entry = recorder.record(actorWithoutUnit, request());

        assertNull(entry.unit());
        assertTrue(emitted.list.getFirst().getFormattedMessage().contains("unit=null"));
    }

    private ActorContext actor() {
        return new ActorContext(
            userId,
            tenantId,
            UnitReference.of(unitId),
            Set.of("OWNER"),
            Set.of("user:create")
        );
    }

    private AuditRecordRequest request() {
        return new AuditRecordRequest("user.created", "user", "123", Map.of("source", "contract-test"));
    }
}
