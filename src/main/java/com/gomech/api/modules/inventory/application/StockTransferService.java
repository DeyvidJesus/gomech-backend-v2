package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateTransferRequest;
import com.gomech.api.modules.inventory.api.dto.StockTransferResponse;
import com.gomech.api.modules.inventory.domain.InsufficientStockException;
import com.gomech.api.modules.inventory.domain.InvalidStockTransferException;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.TransferStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.InventoryMovement;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockTransfer;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockTransferItem;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.UnitStock;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockTransferRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final UnitStockRepository unitStockRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    @Transactional
    public StockTransferResponse createTransfer(CreateTransferRequest request, UUID tenantId, UUID userId) {
        if (request.sourceUnitId().equals(request.destinationUnitId())) {
            throw new InvalidStockTransferException("A filial de origem e de destino não podem ser iguais.");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidStockTransferException("A transferência deve conter pelo menos um item.");
        }

        // Validação de estoque disponível na filial de origem
        for (CreateTransferRequest.TransferItemRequest itemReq : request.items()) {
            Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(itemReq.productId(), tenantId)
                .orElseThrow(() -> new ProductNotFoundException(itemReq.productId()));

            UnitStock sourceStock = unitStockRepository.findByTenantIdAndUnitIdAndProductId(
                tenantId, request.sourceUnitId(), itemReq.productId()
            ).orElseThrow(() -> new InsufficientStockException(itemReq.productId(), itemReq.quantity(), BigDecimal.ZERO));

            if (sourceStock.getAvailableStock().compareTo(itemReq.quantity()) < 0) {
                throw new InsufficientStockException(itemReq.productId(), itemReq.quantity(), sourceStock.getAvailableStock());
            }
        }

        long count = transferRepository.countByTenantId(tenantId) + 1;
        String transferNumber = String.format("TRF-%05d", count);

        StockTransfer transfer = StockTransfer.builder()
            .tenantId(tenantId)
            .transferNumber(transferNumber)
            .sourceUnitId(request.sourceUnitId())
            .destinationUnitId(request.destinationUnitId())
            .status(TransferStatus.PENDING)
            .notes(request.notes())
            .requestedByUserId(userId)
            .items(new ArrayList<>())
            .build();

        for (CreateTransferRequest.TransferItemRequest itemReq : request.items()) {
            StockTransferItem item = StockTransferItem.builder()
                .transfer(transfer)
                .tenantId(tenantId)
                .productId(itemReq.productId())
                .quantity(itemReq.quantity())
                .notes(itemReq.notes())
                .build();
            transfer.getItems().add(item);
        }

        StockTransfer saved = transferRepository.save(transfer);
        log.info("Transferência de estoque {} ({}) criada no tenant {}", saved.getId(), transferNumber, tenantId);

        return toResponse(saved);
    }

    @Transactional
    public StockTransferResponse completeTransfer(UUID transferId, UUID tenantId, UUID userId) {
        StockTransfer transfer = transferRepository.findByIdAndTenantIdWithItems(transferId, tenantId)
            .orElseThrow(() -> new InvalidStockTransferException("Transferência não encontrada: " + transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            log.warn("Transferência {} já foi concluída anteriormente", transferId);
            return toResponse(transfer);
        }

        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new InvalidStockTransferException("Não é possível concluir uma transferência cancelada.");
        }

        for (StockTransferItem item : transfer.getItems()) {
            Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(item.getProductId(), tenantId)
                .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));

            // 1. Atualiza origem (deduz físico)
            UnitStock sourceStock = unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
                tenantId, transfer.getSourceUnitId(), item.getProductId()
            ).orElseThrow(() -> new InsufficientStockException(item.getProductId(), item.getQuantity(), BigDecimal.ZERO));

            if (sourceStock.getQuantityOnHand().compareTo(item.getQuantity()) < 0) {
                throw new InsufficientStockException(item.getProductId(), item.getQuantity(), sourceStock.getQuantityOnHand());
            }

            sourceStock.setQuantityOnHand(sourceStock.getQuantityOnHand().subtract(item.getQuantity()));
            unitStockRepository.save(sourceStock);

            // 2. Atualiza destino (incrementa físico)
            UnitStock destStock = unitStockRepository.findByTenantIdAndUnitIdAndProductIdForUpdate(
                tenantId, transfer.getDestinationUnitId(), item.getProductId()
            ).orElseGet(() -> {
                UnitStock newStock = UnitStock.builder()
                    .tenantId(tenantId)
                    .unitId(transfer.getDestinationUnitId())
                    .productId(item.getProductId())
                    .quantityOnHand(BigDecimal.ZERO)
                    .quantityReserved(BigDecimal.ZERO)
                    .minStock(BigDecimal.valueOf(product.getMinStock()))
                    .build();
                return unitStockRepository.save(newStock);
            });

            destStock.setQuantityOnHand(destStock.getQuantityOnHand().add(item.getQuantity()));
            unitStockRepository.save(destStock);

            // 3. Registra movimentação de saída na origem
            InventoryMovement outMovement = InventoryMovement.builder()
                .tenantId(tenantId)
                .unitId(transfer.getSourceUnitId())
                .productId(item.getProductId())
                .userId(userId)
                .type(MovementType.OUT)
                .quantity(item.getQuantity().intValue())
                .reason(MovementReason.TRANSFER_OUT)
                .referenceId(transfer.getId())
                .unitCostPrice(product.getCostPrice())
                .unitSellingPrice(product.getSellingPrice())
                .totalCostPrice(product.getCostPrice().multiply(item.getQuantity()))
                .notes("Transferência " + transfer.getTransferNumber() + " enviada para filial " + transfer.getDestinationUnitId())
                .build();
            movementRepository.save(outMovement);

            // 4. Registra movimentação de entrada no destino
            InventoryMovement inMovement = InventoryMovement.builder()
                .tenantId(tenantId)
                .unitId(transfer.getDestinationUnitId())
                .productId(item.getProductId())
                .userId(userId)
                .type(MovementType.IN)
                .quantity(item.getQuantity().intValue())
                .reason(MovementReason.TRANSFER_IN)
                .referenceId(transfer.getId())
                .unitCostPrice(product.getCostPrice())
                .unitSellingPrice(product.getSellingPrice())
                .totalCostPrice(product.getCostPrice().multiply(item.getQuantity()))
                .notes("Transferência " + transfer.getTransferNumber() + " recebida da filial " + transfer.getSourceUnitId())
                .build();
            movementRepository.save(inMovement);
        }

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setReceivedByUserId(userId);
        transfer.setCompletedAt(Instant.now());
        StockTransfer saved = transferRepository.save(transfer);

        log.info("Transferência {} concluída com sucesso no tenant {}", transfer.getTransferNumber(), tenantId);
        return toResponse(saved);
    }

    @Transactional
    public StockTransferResponse cancelTransfer(UUID transferId, String reason, UUID tenantId) {
        StockTransfer transfer = transferRepository.findByIdAndTenantIdWithItems(transferId, tenantId)
            .orElseThrow(() -> new InvalidStockTransferException("Transferência não encontrada: " + transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new InvalidStockTransferException("Não é possível cancelar uma transferência já concluída.");
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCanceledAt(Instant.now());
        transfer.setCancellationReason(reason);
        StockTransfer saved = transferRepository.save(transfer);

        log.info("Transferência {} cancelada no tenant {}", transfer.getTransferNumber(), tenantId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(UUID transferId, UUID tenantId) {
        StockTransfer transfer = transferRepository.findByIdAndTenantIdWithItems(transferId, tenantId)
            .orElseThrow(() -> new InvalidStockTransferException("Transferência não encontrada: " + transferId));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Page<StockTransferResponse> listTransfers(UUID tenantId, UUID unitId, Pageable pageable) {
        return transferRepository.findAllByTenantAndUnit(tenantId, unitId, pageable)
            .map(this::toResponse);
    }

    private StockTransferResponse toResponse(StockTransfer t) {
        List<UUID> productIds = t.getItems().stream().map(StockTransferItem::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<StockTransferResponse.TransferItemResponse> itemResponses = t.getItems().stream().map(item -> {
            Product p = productMap.get(item.getProductId());
            return new StockTransferResponse.TransferItemResponse(
                item.getId(),
                item.getProductId(),
                p != null ? p.getSkuCode() : "N/A",
                p != null ? p.getName() : "N/A",
                item.getQuantity(),
                item.getNotes()
            );
        }).toList();

        return new StockTransferResponse(
            t.getId(),
            t.getTenantId(),
            t.getTransferNumber(),
            t.getSourceUnitId(),
            t.getDestinationUnitId(),
            t.getStatus(),
            t.getNotes(),
            t.getRequestedByUserId(),
            t.getReceivedByUserId(),
            t.getCompletedAt(),
            t.getCanceledAt(),
            t.getCancellationReason(),
            itemResponses,
            t.getCreatedAt()
        );
    }
}
