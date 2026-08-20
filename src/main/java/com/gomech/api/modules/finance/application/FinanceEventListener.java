package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.PayableDtos;
import com.gomech.api.modules.finance.api.dto.ReceivableDtos;
import com.gomech.api.modules.finance.domain.DreCategoryType;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.inventory.events.InventoryPurchaseCreatedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCanceledEvent;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import com.gomech.api.modules.operations.events.WorkOrderReopenedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceEventListener {

    private final ReceivableService receivableService;
    private final PayableService payableService;
    private final FinanceCategoryService categoryService;

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        log.info("Recebido WorkOrderCompletedEvent no Finance para OS {} (R$ {})", event.workOrderId(), event.totalAmount());

        try {
            FinanceCategory cat = categoryService.getOrCreateDefaultCategory(
                    event.tenantId(),
                    "Serviços Automotivos e Manutenção",
                    TransactionType.CREDIT,
                    DreCategoryType.GROSS_REVENUE
            );

            LocalDate dueDate = event.completedAt() != null
                    ? event.completedAt().toLocalDate()
                    : LocalDate.now();

            ReceivableDtos.Create createDto = ReceivableDtos.Create.builder()
                    .unitId(event.unitId())
                    .customerId(event.customerId())
                    .workOrderId(event.workOrderId())
                    .orderNumber(event.orderNumber())
                    .description("Ordem de Serviço " + (event.orderNumber() != null ? event.orderNumber() : event.workOrderId()))
                    .amount(event.totalAmount())
                    .dueDate(dueDate)
                    .categoryId(cat.getId())
                    .sourceCorrelationId("WO_COMPLETED_" + event.workOrderId())
                    .notes("Gerado automaticamente pela conclusão da OS")
                    .build();

            receivableService.createReceivable(createDto, event.tenantId());
        } catch (Exception e) {
            log.error("Erro ao gerar conta a receber para OS {}: {}", event.workOrderId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderReopened(WorkOrderReopenedEvent event) {
        log.info("Recebido WorkOrderReopenedEvent no Finance para OS {}", event.workOrderId());
        try {
            receivableService.reverseReceivableForWorkOrder(event.workOrderId(), "Reabertura da Ordem de Serviço", event.tenantId());
        } catch (Exception e) {
            log.error("Erro ao estornar conta a receber para reabertura da OS {}: {}", event.workOrderId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCanceled(WorkOrderCanceledEvent event) {
        log.info("Recebido WorkOrderCanceledEvent no Finance para OS {}", event.workOrderId());
        try {
            receivableService.reverseReceivableForWorkOrder(event.workOrderId(), "Cancelamento da Ordem de Serviço", event.tenantId());
        } catch (Exception e) {
            log.error("Erro ao cancelar/estornar conta a receber da OS {}: {}", event.workOrderId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onInventoryPurchaseCreated(InventoryPurchaseCreatedEvent event) {
        log.info("Recebido InventoryPurchaseCreatedEvent no Finance para compra {} (R$ {})", event.purchaseId(), event.totalAmount());

        try {
            FinanceCategory cat = categoryService.getOrCreateDefaultCategory(
                    event.tenantId(),
                    "Compras de Peças e Insumos (Estoque)",
                    TransactionType.DEBIT,
                    DreCategoryType.VARIABLE_COST
            );

            PayableDtos.Create createDto = PayableDtos.Create.builder()
                    .unitId(event.unitId())
                    .supplierName(event.supplierName())
                    .inventoryPurchaseId(event.purchaseId())
                    .description(event.description() != null ? event.description() : "Compra de Estoque / NF " + event.invoiceNumber())
                    .amount(event.totalAmount())
                    .dueDate(event.dueDate() != null ? event.dueDate() : LocalDate.now().plusDays(30))
                    .categoryId(cat.getId())
                    .sourceCorrelationId("INV_PURCHASE_" + event.purchaseId())
                    .notes("Gerado automaticamente pela entrada de compra de estoque")
                    .build();

            payableService.createPayable(createDto, event.tenantId());
        } catch (Exception e) {
            log.error("Erro ao gerar conta a pagar para compra de estoque {}: {}", event.purchaseId(), e.getMessage(), e);
        }
    }
}
