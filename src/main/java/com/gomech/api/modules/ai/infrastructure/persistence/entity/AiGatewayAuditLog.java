package com.gomech.api.modules.ai.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_gateway_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGatewayAuditLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "capability", nullable = false, length = 50)
    private String capability;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "redacted_prompt_summary", length = 1000)
    private String redactedPromptSummary;

    @Column(name = "redacted_response_summary", length = 1000)
    private String redactedResponseSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
