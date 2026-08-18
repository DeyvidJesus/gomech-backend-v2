package com.gomech.api.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the correlation id for the request and guarantees it is gone again afterwards.
 *
 * <p>This is the production source of the value {@code EventMetadataFactory} reads: without it the
 * MDC key is never populated and every event carries a null correlation id.
 *
 * <p>It is the outermost filter, ahead of {@code TenantFilter}, so that anything logged during the
 * rest of the chain — including tenant resolution and authentication failures — is already
 * correlated. Its {@code finally} block is therefore the last code to run for the request, which is
 * what keeps the id off a pooled container thread once the response is done.
 *
 * <p>An inbound {@code X-Correlation-ID} is honoured when it is a safe, bounded value, so a caller
 * or an upstream service can tie its own logs to ours. Anything else is quietly replaced by a
 * generated id: a request is never rejected over a correlation header. The id in effect is echoed
 * back on the response so the caller can record what it was.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request.getHeader(CorrelationId.HEADER));

        try (CorrelationId.Scope ignored = CorrelationId.scope(correlationId)) {
            response.setHeader(CorrelationId.HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            // The scope restores the previous value, which on a container thread is normally none.
            // Clearing as well means a thread cannot retain an id even if one was somehow left behind.
            CorrelationId.clear();
        }
    }

    private String resolveCorrelationId(String inbound) {
        return CorrelationId.isAcceptable(inbound) ? inbound : CorrelationId.generate();
    }
}
