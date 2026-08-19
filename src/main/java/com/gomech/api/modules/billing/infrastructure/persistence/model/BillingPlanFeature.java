package com.gomech.api.modules.billing.infrastructure.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_plan_features")
@Getter
@Setter
public class BillingPlanFeature {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private BillingPlan plan;

    @Column(name = "feature_code", nullable = false, length = 100)
    private String featureCode;

    @Column(name = "limit_value", nullable = false)
    private Long limitValue = -1L; // -1 = Unlimited

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
