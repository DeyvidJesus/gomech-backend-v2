package com.gomech.api.modules.analytics.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analytics_work_order_projections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsWorkOrderProjection {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "mechanic_user_id")
    private UUID mechanicUserId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "parts_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal partsAmount;

    @Column(name = "services_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal servicesAmount;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "turnaround_hours", precision = 10, scale = 2)
    private BigDecimal turnaroundHours;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
