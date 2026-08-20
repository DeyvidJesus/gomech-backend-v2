package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateProductRequest;
import com.gomech.api.modules.inventory.api.dto.ProductResponse;
import com.gomech.api.modules.inventory.api.dto.ProductSummaryResponse;
import com.gomech.api.modules.inventory.api.dto.UpdateProductRequest;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.UnitOfMeasure;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UnitStockRepository unitStockRepository;
    private final InventoryMovementRepository movementRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID tenantId, UUID userId) {
        if (productRepository.existsByTenantIdAndSkuCodeAndDeletedAtIsNull(tenantId, request.skuCode())) {
            throw new IllegalArgumentException("Já existe um produto com o SKU '" + request.skuCode() + "' nesta oficina.");
        }

        Product product = Product.builder()
            .tenantId(tenantId)
            .unitId(request.unitId())
            .supplierId(request.supplierId())
            .skuCode(request.skuCode().trim().toUpperCase())
            .name(request.name().trim())
            .category(request.category() != null ? request.category().trim() : null)
            .barcode(request.barcode() != null ? request.barcode().trim() : null)
            .brand(request.brand() != null ? request.brand().trim() : null)
            .unitOfMeasure(request.unitOfMeasure() != null ? request.unitOfMeasure() : UnitOfMeasure.UN)
            .costPrice(request.costPrice())
            .sellingPrice(request.sellingPrice())
            .minStock(request.minStock() != null ? request.minStock() : 0)
            .locationInWarehouse(request.locationInWarehouse())
            .active(true)
            .build();

        Product savedProduct = productRepository.save(product);
        log.info("Produto {} criado com sucesso no tenant {}", savedProduct.getId(), tenantId);

        // Se informou estoque inicial para uma filial específica, inicializa unit_stock e registra movimentação
        if (request.initialStockQuantity() != null && request.initialStockQuantity().compareTo(BigDecimal.ZERO) > 0) {
            UUID targetUnitId = request.initialStockUnitId() != null ? request.initialStockUnitId() : request.unitId();
            if (targetUnitId != null) {
                UnitStock unitStock = UnitStock.builder()
                    .tenantId(tenantId)
                    .unitId(targetUnitId)
                    .productId(savedProduct.getId())
                    .quantityOnHand(request.initialStockQuantity())
                    .quantityReserved(BigDecimal.ZERO)
                    .minStock(BigDecimal.valueOf(request.minStock() != null ? request.minStock() : 0))
                    .build();
                unitStockRepository.save(unitStock);

                InventoryMovement movement = InventoryMovement.builder()
                    .tenantId(tenantId)
                    .unitId(targetUnitId)
                    .productId(savedProduct.getId())
                    .userId(userId)
                    .type(MovementType.IN)
                    .quantity(request.initialStockQuantity().intValue())
                    .reason(MovementReason.INITIAL_BALANCE)
                    .unitCostPrice(request.costPrice())
                    .unitSellingPrice(request.sellingPrice())
                    .totalCostPrice(request.costPrice().multiply(request.initialStockQuantity()))
                    .notes("Saldo inicial na criação do produto")
                    .build();
                movementRepository.save(movement);
            }
        }

        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request, UUID tenantId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        if (productRepository.existsByTenantIdAndSkuCodeAndIdNotAndDeletedAtIsNull(tenantId, request.skuCode(), productId)) {
            throw new IllegalArgumentException("Já existe outro produto com o SKU '" + request.skuCode() + "'.");
        }

        product.setUnitId(request.unitId());
        product.setSupplierId(request.supplierId());
        product.setSkuCode(request.skuCode().trim().toUpperCase());
        product.setName(request.name().trim());
        product.setCategory(request.category() != null ? request.category().trim() : null);
        product.setBarcode(request.barcode() != null ? request.barcode().trim() : null);
        product.setBrand(request.brand() != null ? request.brand().trim() : null);
        product.setUnitOfMeasure(request.unitOfMeasure());
        product.setCostPrice(request.costPrice());
        product.setSellingPrice(request.sellingPrice());
        product.setMinStock(request.minStock() != null ? request.minStock() : 0);
        product.setLocationInWarehouse(request.locationInWarehouse());
        if (request.active() != null) {
            product.setActive(request.active());
        }

        Product updated = productRepository.save(product);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId, UUID tenantId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(UUID tenantId, String search, String category, Boolean active, Pageable pageable) {
        return productRepository.findAllByTenantWithFilters(tenantId, search, category, active, pageable)
            .map(this::toResponse);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID tenantId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setDeletedAt(Instant.now());
        product.setActive(false);
        productRepository.save(product);
        log.info("Produto {} marcado como deletado no tenant {}", productId, tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<ProductSummaryResponse> findProductSummary(UUID productId, UUID tenantId) {
        return productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .map(p -> new ProductSummaryResponse(
                p.getId(),
                p.getSkuCode(),
                p.getName(),
                p.getCategory(),
                p.getBarcode(),
                p.getBrand(),
                p.getUnitOfMeasure(),
                p.getCostPrice(),
                p.getSellingPrice(),
                p.getMinStock(),
                p.isActive()
            ));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
            p.getId(),
            p.getTenantId(),
            p.getUnitId(),
            p.getSupplierId(),
            p.getSkuCode(),
            p.getName(),
            p.getCategory(),
            p.getBarcode(),
            p.getBrand(),
            p.getUnitOfMeasure(),
            p.getCostPrice(),
            p.getSellingPrice(),
            p.getMinStock(),
            p.getCurrentStockCalculated(),
            p.getLocationInWarehouse(),
            p.isActive(),
            p.getVersion(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
