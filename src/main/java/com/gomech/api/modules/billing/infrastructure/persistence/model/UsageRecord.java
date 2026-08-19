package com.gomech.api.modules.billing.infrastructure.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
@Getter
@Setter
public class UsageRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "dimension", nullable = false, length = 50)
    private String dimension;

    @Column(name = "amount", nullable = false)
    private Long amount = 0L;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
