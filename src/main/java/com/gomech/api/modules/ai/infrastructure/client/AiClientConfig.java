package com.gomech.api.modules.ai.infrastructure.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gomech.ai")
@Getter
@Setter
public class AiClientConfig {

    private String baseUrl = "http://localhost:8088/ai";
    private String apiKey = "gm-ai-default-key";
    private String serviceSecret = "gm-ai-internal-hmac-secret";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 3;
    private long backoffBaseMs = 150;
    private boolean mockEnabled = true;
}
