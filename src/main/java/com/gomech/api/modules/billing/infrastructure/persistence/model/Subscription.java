package com.gomech.api.modules.billing.infrastructure.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class Subscription {

    @Id
    private UUID id = UUID.randomUUID();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @TenantId
    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private BillingPlan plan;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "TRIALING";

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "cancel_at_period_end")
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "gateway_subscription_id")
    private String gatewaySubscriptionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
