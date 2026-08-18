package com.gomech.api.core.logging;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The correlation id that ties together everything done while handling one request: log lines, and
 * the metadata carried by every domain event published during it.
 *
 * <p>This is the single place that knows the MDC key, so the logging pattern, the filter that
 * establishes the id, and {@code EventMetadataFactory} which reads it cannot drift apart.
 */
public final class CorrelationId {

    /** MDC key. Referenced by the logging pattern in {@code application.yml}. */
    public static final String MDC_KEY = "correlation_id";

    /** Request header used to accept an inbound id and to echo the one in effect. */
    public static final String HEADER = "X-Correlation-ID";

    /**
     * A caller-supplied id is echoed into every log line, so it is accepted only in a bounded,
     * printable form. This keeps a hostile value from injecting newlines into the log or bloating
     * every record; anything else is replaced by a generated id rather than rejected as an error.
     */
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isAcceptable(String candidate) {
        return candidate != null && ACCEPTABLE.matcher(candidate).matches();
    }

    /** The id in effect on this thread, or null when there is none. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Puts {@code correlationId} in scope until the returned handle is closed, restoring whatever was
     * in scope before. Restoring rather than clearing is what makes this safe to nest, and keeps an
     * inner scope from wiping the surrounding request's id.
     */
    public static Scope scope(String correlationId) {
        String previous = MDC.get(MDC_KEY);
        if (correlationId != null) {
            MDC.put(MDC_KEY, correlationId);
        }
        return () -> {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        };
    }

    /** Closeable handle that restores the previous correlation id. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
