package com.gomech.api.modules.billing.infrastructure.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events")
@Getter
@Setter
public class ProcessedWebhook {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "source", nullable = false, length = 50)
    private String source = "PAGARME";

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PROCESSED";

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;
}
