package com.gomech.api.modules.tools.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_usages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tool_id", nullable = false)
    private UUID toolId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "mechanic_user_id")
    private UUID mechanicUserId;

    @CreationTimestamp
    @Column(name = "checked_out_at", nullable = false, updatable = false)
    private Instant checkedOutAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
