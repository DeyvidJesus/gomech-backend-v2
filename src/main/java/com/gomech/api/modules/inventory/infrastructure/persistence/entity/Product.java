package com.gomech.api.modules.inventory.infrastructure.persistence.entity;

import com.gomech.api.modules.inventory.domain.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "sku_code", nullable = false, length = 100)
    private String skuCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String barcode;

    @Column(length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    @Builder.Default
    private UnitOfMeasure unitOfMeasure = UnitOfMeasure.UN;

    @Column(name = "cost_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "selling_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "min_stock", nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    @Column(name = "current_stock_calculated", nullable = false)
    @Builder.Default
    private Integer currentStockCalculated = 0;

    @Column(name = "location_in_warehouse", length = 100)
    private String locationInWarehouse;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
