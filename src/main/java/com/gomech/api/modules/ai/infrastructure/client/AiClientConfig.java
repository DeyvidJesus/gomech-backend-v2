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

    private String baseUrl = "http://localhost:8000";
    private String serviceSecret;
    private String idTokenAudience;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 3;
    private long backoffBaseMs = 150;
}
