package com.gomech.api.modules.inventory.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "unit_stocks",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_unit_stocks_tenant_unit_product",
        columnNames = {"tenant_id", "unit_id", "product_id"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "min_stock", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "max_stock", precision = 10, scale = 2)
    private BigDecimal maxStock;

    @Column(name = "shelf_location", length = 100)
    private String shelfLocation;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public BigDecimal getAvailableStock() {
        BigDecimal onHand = this.quantityOnHand != null ? this.quantityOnHand : BigDecimal.ZERO;
        BigDecimal reserved = this.quantityReserved != null ? this.quantityReserved : BigDecimal.ZERO;
        return onHand.subtract(reserved);
    }
}
