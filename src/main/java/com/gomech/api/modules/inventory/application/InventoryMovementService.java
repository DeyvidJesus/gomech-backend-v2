package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.InventoryMovementResponse;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> listMovements(
        UUID tenantId,
        UUID unitId,
        UUID productId,
        MovementType type,
        MovementReason reason,
        Pageable pageable
    ) {
        Page<InventoryMovement> page = movementRepository.findAllByTenantWithFilters(
            tenantId, unitId, productId, type, reason, pageable
        );

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> productIds = page.getContent().stream()
            .map(InventoryMovement::getProductId)
            .distinct()
            .toList();

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        return page.map(m -> {
            Product p = productMap.get(m.getProductId());
            return new InventoryMovementResponse(
                m.getId(),
                m.getTenantId(),
                m.getUnitId(),
                m.getProductId(),
                p != null ? p.getSkuCode() : "N/A",
                p != null ? p.getName() : "N/A",
                m.getUserId(),
                m.getType(),
                m.getQuantity(),
                m.getReason(),
                m.getReferenceId(),
                m.getUnitCostPrice(),
                m.getUnitSellingPrice(),
                m.getTotalCostPrice(),
                m.getBatchNumber(),
                m.getNotes(),
                m.getIdempotencyKey(),
                m.getCreatedAt()
            );
        });
    }
}
