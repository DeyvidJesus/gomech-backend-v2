package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.AdjustStockRequest;
import com.gomech.api.modules.inventory.api.dto.LowStockProductResponse;
import com.gomech.api.modules.inventory.api.dto.UnitStockResponse;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final UnitStockRepository unitStockRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<UnitStockResponse> getUnitStocks(UUID tenantId, UUID unitId) {
        List<UnitStock> stocks = unitStockRepository.findAllByTenantIdAndUnitId(tenantId, unitId);
        if (stocks.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = stocks.stream().map(UnitStock::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        return stocks.stream().map(s -> {
            Product p = productMap.get(s.getProductId());
            return new UnitStockResponse(
                s.getId(),
                s.getTenantId(),
                s.getUnitId(),
                s.getProductId(),
                p != null ? p.getSkuCode() : "N/A",
                p != null ? p.getName() : "N/A",
                s.getQuantityOnHand(),
                s.getQuantityReserved(),
                s.getAvailableStock(),
                s.getMinStock(),
                s.getMaxStock(),
                s.getShelfLocation(),
                s.getUpdatedAt()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public UnitStockResponse getStockForProduct(UUID tenantId, UUID unitId, UUID productId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        UnitStock stock = unitStockRepository.findByTenantIdAndUnitIdAndProductId(tenantId, unitId, productId)
            .orElseGet(() -> UnitStock.builder()
                .tenantId(tenantId)
                .unitId(unitId)
                .productId(productId)
                .quantityOnHand(BigDecimal.ZERO)
                .quantityReserved(BigDecimal.ZERO)
                .minStock(BigDecimal.valueOf(product.getMinStock()))
                .build());

        return new UnitStockResponse(
            stock.getId(),
            tenantId,
            unitId,
            productId,
            product.getSkuCode(),
            product.getName(),
            stock.getQuantityOnHand(),
            stock.getQuantityReserved(),
            stock.getAvailableStock(),
            stock.getMinStock(),
            stock.getMaxStock(),
            stock.getShelfLocation(),
            stock.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailableStock(UUID tenantId, UUID unitId, UUID productId) {
        return unitStockRepository.findByTenantIdAndUnitIdAndProductId(tenantId, unitId, productId)
            .map(UnitStock::getAvailableStock)
            .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public UnitStockResponse adjustStock(AdjustStockRequest request, UUID tenantId, UUID userId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.productId(), tenantId)
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        UnitStock stock = unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
            tenantId, request.unitId(), request.productId()
        ).orElseGet(() -> {
            UnitStock newStock = UnitStock.builder()
                .tenantId(tenantId)
                .unitId(request.unitId())
                .productId(request.productId())
                .quantityOnHand(BigDecimal.ZERO)
                .quantityReserved(BigDecimal.ZERO)
                .minStock(BigDecimal.valueOf(product.getMinStock()))
                .build();
            return unitStockRepository.save(newStock);
        });

        BigDecimal oldOnHand = stock.getQuantityOnHand();
        BigDecimal newOnHand = request.newQuantityOnHand();
        BigDecimal diff = newOnHand.subtract(oldOnHand);

        stock.setQuantityOnHand(newOnHand);
        UnitStock savedStock = unitStockRepository.save(stock);

        // Gera registro imutável no ledger de movimentações
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            MovementType movementType = diff.compareTo(BigDecimal.ZERO) > 0 ? MovementType.IN : MovementType.OUT;
            MovementReason movementReason = request.reason() != null ? request.reason() :
                (movementType == MovementType.IN ? MovementReason.ADJUSTMENT_INCREASE : MovementReason.ADJUSTMENT_DECREASE);

            InventoryMovement movement = InventoryMovement.builder()
                .tenantId(tenantId)
                .unitId(request.unitId())
                .productId(request.productId())
                .userId(userId)
                .type(movementType)
                .quantity(diff.abs().intValue())
                .reason(movementReason)
                .unitCostPrice(product.getCostPrice())
                .unitSellingPrice(product.getSellingPrice())
                .totalCostPrice(product.getCostPrice().multiply(diff.abs()))
                .notes(request.notes() != null ? request.notes() : "Ajuste manual de inventário de " + oldOnHand + " para " + newOnHand)
                .build();
            movementRepository.save(movement);
        }

        log.info("Estoque ajustado para produto {} na unidade {}: {} -> {}", request.productId(), request.unitId(), oldOnHand, newOnHand);

        return new UnitStockResponse(
            savedStock.getId(),
            tenantId,
            request.unitId(),
            request.productId(),
            product.getSkuCode(),
            product.getName(),
            savedStock.getQuantityOnHand(),
            savedStock.getQuantityReserved(),
            savedStock.getAvailableStock(),
            savedStock.getMinStock(),
            savedStock.getMaxStock(),
            savedStock.getShelfLocation(),
            savedStock.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<LowStockProductResponse> getLowStockAlerts(UUID tenantId, UUID unitId) {
        List<UnitStock> lowStocks = unitStockRepository.findLowStockByTenantAndUnit(tenantId, unitId);
        if (lowStocks.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = lowStocks.stream().map(UnitStock::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<LowStockProductResponse> result = new ArrayList<>();
        for (UnitStock s : lowStocks) {
            Product p = productMap.get(s.getProductId());
            if (p != null && p.getDeletedAt() == null && p.isActive()) {
                BigDecimal deficit = s.getMinStock().subtract(s.getQuantityOnHand());
                if (deficit.compareTo(BigDecimal.ZERO) >= 0) {
                    result.add(new LowStockProductResponse(
                        p.getId(),
                        p.getSkuCode(),
                        p.getName(),
                        s.getUnitId(),
                        s.getQuantityOnHand(),
                        s.getQuantityReserved(),
                        s.getAvailableStock(),
                        s.getMinStock(),
                        deficit
                    ));
                }
            }
        }
        return result;
    }
}
