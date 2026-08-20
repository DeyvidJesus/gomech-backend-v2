package com.gomech.api.modules.inventory.infrastructure.persistence.repository;

import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsByTenantIdAndSkuCodeAndDeletedAtIsNull(UUID tenantId, String skuCode);

    boolean existsByTenantIdAndSkuCodeAndIdNotAndDeletedAtIsNull(UUID tenantId, String skuCode, UUID id);

    @Query("""
        SELECT p FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.deletedAt IS NULL
          AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.skuCode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:category IS NULL OR p.category = :category)
          AND (:active IS NULL OR p.active = :active)
    """)
    Page<Product> findAllByTenantWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("search") String search,
        @Param("category") String category,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
