package com.gomech.api.modules.ai.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AiGatewayMetrics {

    private final MeterRegistry meterRegistry;

    public void recordSuccess(String capability, String model, int promptTokens, int completionTokens, long latencyMs) {
        Counter.builder("ai.gateway.requests.total")
                .tag("capability", capability)
                .tag("status", "SUCCESS")
                .tag("model", model)
                .register(meterRegistry)
                .increment();

        Counter.builder("ai.gateway.tokens.total")
                .tag("capability", capability)
                .tag("type", "prompt")
                .register(meterRegistry)
                .increment(promptTokens);

        Counter.builder("ai.gateway.tokens.total")
                .tag("capability", capability)
                .tag("type", "completion")
                .register(meterRegistry)
                .increment(completionTokens);

        Timer.builder("ai.gateway.latency")
                .tag("capability", capability)
                .tag("model", model)
                .register(meterRegistry)
                .record(Duration.ofMillis(latencyMs));
    }

    public void recordError(String capability, String status, String errorCode) {
        Counter.builder("ai.gateway.requests.total")
                .tag("capability", capability)
                .tag("status", status)
                .tag("error_code", errorCode != null ? errorCode : "UNKNOWN")
                .register(meterRegistry)
                .increment();

        Counter.builder("ai.gateway.errors.total")
                .tag("capability", capability)
                .tag("error_code", errorCode != null ? errorCode : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }
}
