package com.gomech.api.core.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @BeforeEach
    @AfterEach
    void resetContext() {
        CorrelationId.clear();
    }

    @Test
    void generates_a_correlation_id_when_the_request_carries_none() throws Exception {
        AtomicReference<String> seenByHandler = new AtomicReference<>();

        MockHttpServletResponse response = run(request(null), seenByHandler);

        String generated = seenByHandler.get();
        assertNotNull(generated, "every request must be correlated, even without an inbound id");
        assertDoesNotThrow(() -> UUID.fromString(generated));
        assertEquals(generated, response.getHeader(CorrelationId.HEADER),
            "the id in effect is echoed so the caller can record it");
    }

    @Test
    void propagates_an_acceptable_inbound_correlation_id() throws Exception {
        AtomicReference<String> seenByHandler = new AtomicReference<>();

        MockHttpServletResponse response = run(request("upstream-request-42"), seenByHandler);

        assertEquals("upstream-request-42", seenByHandler.get(),
            "an upstream id must be kept so its logs and ours line up");
        assertEquals("upstream-request-42", response.getHeader(CorrelationId.HEADER));
    }

    @Test
    void replaces_an_unusable_inbound_correlation_id_instead_of_failing_the_request() throws Exception {
        List<String> hostileValues = List.of(
            "",
            "   ",
            "has spaces",
            "line\nbreak",
            "carriage\rreturn",
            "a".repeat(65),
            "semi;colon"
        );

        for (String hostile : hostileValues) {
            AtomicReference<String> seenByHandler = new AtomicReference<>();

            run(request(hostile), seenByHandler);

            String effective = seenByHandler.get();
            assertNotNull(effective, "rejecting the header must not leave the request uncorrelated");
            assertNotEquals(hostile, effective, "unusable inbound id was accepted: '" + hostile + "'");
            assertDoesNotThrow(() -> UUID.fromString(effective));
        }
    }

    @Test
    void context_is_cleared_after_the_request_completes() throws Exception {
        run(request(null), new AtomicReference<>());

        assertNull(CorrelationId.current(), "the id must not outlive the request on this thread");
    }

    @Test
    void context_is_cleared_even_when_the_request_fails() {
        MockHttpServletRequest request = request(null);

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
            (req, res) -> {
                throw new IllegalStateException("handler blew up");
            }));

        assertNull(CorrelationId.current());
    }

    @Test
    void a_second_request_on_the_same_thread_gets_its_own_id() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        run(request("first-request"), first);
        run(request(null), second);

        assertNotEquals(first.get(), second.get(),
            "the previous request's id leaked into the next one on this thread");
        assertNull(CorrelationId.current());
    }

    @Test
    void concurrent_requests_do_not_observe_each_others_ids() throws Exception {
        int requests = 8;
        CountDownLatch allInsideHandler = new CountDownLatch(requests);
        CountDownLatch release = new CountDownLatch(1);
        List<String> observed = new CopyOnWriteArrayList<>();
        List<Thread> threads = new java.util.ArrayList<>();

        for (int i = 0; i < requests; i++) {
            String inbound = "request-" + i;
            Thread thread = new Thread(() -> {
                try {
                    filter.doFilter(request(inbound), new MockHttpServletResponse(), (req, res) -> {
                        // Hold every request inside its handler at once, so the ids genuinely overlap.
                        allInsideHandler.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        observed.add(inbound + "=" + CorrelationId.current());
                    });
                } catch (Exception e) {
                    observed.add(inbound + "=FAILED");
                }
            });
            threads.add(thread);
            thread.start();
        }

        assertTrue(allInsideHandler.await(5, TimeUnit.SECONDS), "requests did not overlap");
        release.countDown();
        for (Thread thread : threads) {
            thread.join(5_000);
        }

        assertEquals(requests, observed.size());
        for (String entry : observed) {
            String[] parts = entry.split("=", 2);
            assertEquals(parts[0], parts[1],
                "a concurrent request observed another request's correlation id: " + entry);
        }
    }

    /**
     * The correlation filter is only useful if it wraps the rest of the chain. This asserts the
     * ordering through the comparator Spring itself uses to sort filters, rather than trusting that
     * the two {@code @Order} values stay in the intended relationship.
     */
    @Test
    void correlation_filter_is_ordered_ahead_of_the_tenant_filter() {
        List<Object> filters = new java.util.ArrayList<>(List.of(
            new com.gomech.api.core.tenancy.TenantFilter(false),
            new CorrelationIdFilter()
        ));

        filters.sort(org.springframework.core.annotation.AnnotationAwareOrderComparator.INSTANCE);

        assertTrue(filters.getFirst() instanceof CorrelationIdFilter,
            "tenant resolution must already be correlated, so the correlation filter runs first");
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<String> seenByHandler)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenByHandler.set(CorrelationId.current());
            }
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest request(String inboundCorrelationId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        if (inboundCorrelationId != null) {
            request.addHeader(CorrelationId.HEADER, inboundCorrelationId);
        }
        return request;
    }
}
