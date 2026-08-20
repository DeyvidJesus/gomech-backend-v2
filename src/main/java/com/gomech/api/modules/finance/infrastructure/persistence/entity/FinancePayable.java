package com.gomech.api.modules.finance.infrastructure.persistence.entity;

import com.gomech.api.modules.finance.domain.PayableStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_payables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancePayable {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "inventory_purchase_id")
    private UUID inventoryPurchaseId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayableStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "source_correlation_id", length = 100, unique = true)
    private String sourceCorrelationId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
