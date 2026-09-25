package com.gomech.api.modules.analytics.application;

import com.gomech.api.modules.analytics.domain.KpiCategory;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsDailyKpiSnapshot;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsFinancialProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsInventoryProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsProcessedEventLog;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsToolProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsWorkOrderProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsDailyKpiSnapshotRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsFinancialProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsInventoryProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsProcessedEventLogRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsToolProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsWorkOrderProjectionRepository;
import com.gomech.api.modules.billing.events.PaymentConfirmedEvent;
import com.gomech.api.modules.billing.events.PaymentFailedEvent;
import com.gomech.api.modules.finance.events.PayableCreatedEvent;
import com.gomech.api.modules.finance.events.ReceivableCreatedEvent;
import com.gomech.api.modules.finance.events.TransactionRecordedEvent;
import com.gomech.api.modules.inventory.events.InventoryPurchaseCreatedEvent;
import com.gomech.api.modules.inventory.events.StockConsumedEvent;
import com.gomech.api.modules.operations.events.AppointmentScheduledEvent;
import com.gomech.api.modules.operations.events.AppointmentStatusChangedEvent;
import com.gomech.api.modules.operations.events.InspectionCompletedEvent;
import com.gomech.api.modules.operations.events.QuoteCreatedEvent;
import com.gomech.api.modules.operations.events.QuoteCustomerDecisionEvent;
import com.gomech.api.modules.operations.events.WorkOrderCanceledEvent;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCreatedEvent;
import com.gomech.api.modules.operations.events.WorkOrderReopenedEvent;
import com.gomech.api.modules.operations.events.WorkOrderStatusChangedEvent;
import com.gomech.api.modules.tools.events.ToolCustodyAssignedEvent;
import com.gomech.api.modules.tools.events.ToolCustodyReturnedEvent;
import com.gomech.api.modules.tools.events.ToolMaintenanceCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventListener {

    private final AnalyticsProcessedEventLogRepository processedEventRepository;
    private final AnalyticsDailyKpiSnapshotRepository dailySnapshotRepository;
    private final AnalyticsWorkOrderProjectionRepository workOrderProjectionRepository;
    private final AnalyticsFinancialProjectionRepository financialProjectionRepository;
    private final AnalyticsInventoryProjectionRepository inventoryProjectionRepository;
    private final AnalyticsToolProjectionRepository toolProjectionRepository;

    // ------------------------------------------------------------------------
    // OPERATIONS EVENT HANDLERS
    // ------------------------------------------------------------------------

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCreated(WorkOrderCreatedEvent event) {
        String eventKey = "WO_CREATED_" + event.workOrderId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.work_order.created")) {
            return;
        }

        AnalyticsWorkOrderProjection projection = AnalyticsWorkOrderProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .workOrderId(event.workOrderId())
                .orderNumber(event.orderNumber())
                .customerId(event.customerId())
                .vehicleId(event.vehicleId())
                .mechanicUserId(event.mechanicUserId())
                .status("CREATED")
                .totalAmount(event.totalAmount() != null ? event.totalAmount() : BigDecimal.ZERO)
                .partsAmount(BigDecimal.ZERO)
                .servicesAmount(BigDecimal.ZERO)
                .itemCount(0)
                .openedAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0L)
                .build();

        workOrderProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "WO_CREATED_COUNT", BigDecimal.ONE, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        String eventKey = "WO_COMPLETED_" + event.workOrderId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.work_order.completed")) {
            return;
        }

        LocalDate completionDate = event.completedAt() != null ? event.completedAt().toLocalDate() : LocalDate.now();

        Optional<AnalyticsWorkOrderProjection> optProj = workOrderProjectionRepository.findByTenantIdAndWorkOrderId(
                event.tenantId(), event.workOrderId());

        AnalyticsWorkOrderProjection proj;
        if (optProj.isPresent()) {
            proj = optProj.get();
            proj.setStatus("COMPLETED");
            proj.setCompletedAt(event.completedAt());
            proj.setTotalAmount(event.totalAmount() != null ? event.totalAmount() : BigDecimal.ZERO);
            proj.setPartsAmount(event.totalPartsAmount() != null ? event.totalPartsAmount() : BigDecimal.ZERO);
            proj.setServicesAmount(event.totalServicesAmount() != null ? event.totalServicesAmount() : BigDecimal.ZERO);
            proj.setItemCount(event.itemCount());
            proj.setMechanicUserId(event.mechanicUserId() != null ? event.mechanicUserId() : proj.getMechanicUserId());
            proj.setUpdatedAt(OffsetDateTime.now());

            if (proj.getOpenedAt() != null && event.completedAt() != null) {
                long durationMinutes = Duration.between(proj.getOpenedAt(), event.completedAt()).toMinutes();
                BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                proj.setTurnaroundHours(hours);
            }
        } else {
            proj = AnalyticsWorkOrderProjection.builder()
                    .id(UUID.randomUUID())
                    .tenantId(event.tenantId())
                    .unitId(event.unitId())
                    .workOrderId(event.workOrderId())
                    .orderNumber(event.orderNumber())
                    .customerId(event.customerId())
                    .vehicleId(event.vehicleId())
                    .mechanicUserId(event.mechanicUserId())
                    .status("COMPLETED")
                    .totalAmount(event.totalAmount() != null ? event.totalAmount() : BigDecimal.ZERO)
                    .partsAmount(event.totalPartsAmount() != null ? event.totalPartsAmount() : BigDecimal.ZERO)
                    .servicesAmount(event.totalServicesAmount() != null ? event.totalServicesAmount() : BigDecimal.ZERO)
                    .itemCount(event.itemCount())
                    .openedAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                    .completedAt(event.completedAt() != null ? event.completedAt() : OffsetDateTime.now())
                    .turnaroundHours(BigDecimal.valueOf(2.5))
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .version(0L)
                    .build();
        }

        workOrderProjectionRepository.save(proj);

        // Atualizar agregados diários de faturamento e throughput
        BigDecimal total = event.totalAmount() != null ? event.totalAmount() : BigDecimal.ZERO;
        BigDecimal parts = event.totalPartsAmount() != null ? event.totalPartsAmount() : BigDecimal.ZERO;
        BigDecimal services = event.totalServicesAmount() != null ? event.totalServicesAmount() : BigDecimal.ZERO;

        recordDailyMetric(event.tenantId(), event.unitId(), completionDate, KpiCategory.OPERATIONS.name(), "WO_COMPLETED_COUNT", BigDecimal.ONE, 1L);
        recordDailyMetric(event.tenantId(), event.unitId(), completionDate, KpiCategory.OPERATIONS.name(), "WO_TOTAL_REVENUE", total, 1L);
        recordDailyMetric(event.tenantId(), event.unitId(), completionDate, KpiCategory.OPERATIONS.name(), "WO_PARTS_REVENUE", parts, 1L);
        recordDailyMetric(event.tenantId(), event.unitId(), completionDate, KpiCategory.OPERATIONS.name(), "WO_SERVICES_REVENUE", services, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderStatusChanged(WorkOrderStatusChangedEvent event) {
        workOrderProjectionRepository.findByTenantIdAndWorkOrderId(event.tenantId(), event.workOrderId())
                .ifPresent(proj -> {
                    proj.setStatus(String.valueOf(event.newStatus()));
                    proj.setUpdatedAt(OffsetDateTime.now());
                    workOrderProjectionRepository.save(proj);
                });
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCanceled(WorkOrderCanceledEvent event) {
        String eventKey = "WO_CANCELED_" + event.workOrderId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.work_order.canceled")) {
            return;
        }

        workOrderProjectionRepository.findByTenantIdAndWorkOrderId(event.tenantId(), event.workOrderId())
                .ifPresent(proj -> {
                    proj.setStatus("CANCELED");
                    proj.setUpdatedAt(OffsetDateTime.now());
                    workOrderProjectionRepository.save(proj);
                });

        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "WO_CANCELED_COUNT", BigDecimal.ONE, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onWorkOrderReopened(WorkOrderReopenedEvent event) {
        String eventKey = "WO_REOPENED_" + event.workOrderId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.work_order.reopened")) {
            return;
        }

        workOrderProjectionRepository.findByTenantIdAndWorkOrderId(event.tenantId(), event.workOrderId())
                .ifPresent(proj -> {
                    proj.setStatus("IN_PROGRESS");
                    proj.setCompletedAt(null);
                    proj.setUpdatedAt(OffsetDateTime.now());
                    workOrderProjectionRepository.save(proj);
                });
    }

    @Async
    @EventListener
    @Transactional
    public void onQuoteCreated(QuoteCreatedEvent event) {
        String eventKey = "QUOTE_CREATED_" + event.quoteId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.quote.created")) {
            return;
        }
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "QUOTE_CREATED_COUNT", BigDecimal.ONE, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onQuoteCustomerDecision(QuoteCustomerDecisionEvent event) {
        String decisionStr = String.valueOf(event.decision());
        String eventKey = "QUOTE_DECISION_" + event.quoteId() + "_" + decisionStr;
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.quote.decision")) {
            return;
        }

        if ("APPROVED".equalsIgnoreCase(decisionStr)) {
            recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "QUOTE_APPROVED_COUNT", BigDecimal.ONE, 1L);
        } else {
            recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "QUOTE_REJECTED_COUNT", BigDecimal.ONE, 1L);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onAppointmentScheduled(AppointmentScheduledEvent event) {
        String eventKey = "APPOINTMENT_SCHEDULED_" + event.appointmentId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.appointment.scheduled")) {
            return;
        }
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "APPOINTMENT_SCHEDULED_COUNT", BigDecimal.ONE, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        String statusStr = String.valueOf(event.newStatus());
        if ("CHECKED_IN".equalsIgnoreCase(statusStr) || "COMPLETED".equalsIgnoreCase(statusStr)) {
            recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "APPOINTMENT_COMPLETED_COUNT", BigDecimal.ONE, 1L);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onInspectionCompleted(InspectionCompletedEvent event) {
        String eventKey = "INSPECTION_COMPLETED_" + event.inspectionId();
        if (!markEventProcessed(event.tenantId(), eventKey, "operations.inspection.completed")) {
            return;
        }
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.OPERATIONS.name(), "INSPECTION_COMPLETED_COUNT", BigDecimal.ONE, 1L);
    }

    // ------------------------------------------------------------------------
    // FINANCE EVENT HANDLERS
    // ------------------------------------------------------------------------

    @Async
    @EventListener
    @Transactional
    public void onReceivableCreated(ReceivableCreatedEvent event) {
        String eventKey = "FIN_REC_CREATED_" + event.receivableId();
        if (!markEventProcessed(event.tenantId(), eventKey, "finance.receivable.created")) {
            return;
        }

        AnalyticsFinancialProjection projection = AnalyticsFinancialProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .recordId(event.receivableId())
                .recordType("RECEIVABLE")
                .categoryName("Receita Ordem de Serviço")
                .type("CREDIT")
                .amount(event.amount())
                .status(event.status())
                .dueDate(event.dueDate())
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0L)
                .build();

        financialProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.FINANCE.name(), "FIN_RECEIVABLES_TOTAL", event.amount(), 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onPayableCreated(PayableCreatedEvent event) {
        String eventKey = "FIN_PAY_CREATED_" + event.payableId();
        if (!markEventProcessed(event.tenantId(), eventKey, "finance.payable.created")) {
            return;
        }

        AnalyticsFinancialProjection projection = AnalyticsFinancialProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .recordId(event.payableId())
                .recordType("PAYABLE")
                .categoryName("Compras & Despesas")
                .type("DEBIT")
                .amount(event.amount())
                .status(event.status())
                .dueDate(event.dueDate())
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0L)
                .build();

        financialProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.FINANCE.name(), "FIN_PAYABLES_TOTAL", event.amount(), 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        String eventKey = "FIN_TX_" + event.transactionId();
        if (!markEventProcessed(event.tenantId(), eventKey, "finance.transaction.recorded")) {
            return;
        }

        AnalyticsFinancialProjection projection = AnalyticsFinancialProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .recordId(event.transactionId())
                .recordType("TRANSACTION")
                .categoryName(event.categoryName())
                .type(event.type())
                .amount(event.amount())
                .status("PAID")
                .paidDate(event.transactionDate())
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0L)
                .build();

        financialProjectionRepository.save(projection);

        if ("CREDIT".equalsIgnoreCase(event.type())) {
            recordDailyMetric(event.tenantId(), event.unitId(), event.transactionDate(), KpiCategory.FINANCE.name(), "FIN_RECEIVABLES_PAID", event.amount(), 1L);
        } else {
            recordDailyMetric(event.tenantId(), event.unitId(), event.transactionDate(), KpiCategory.FINANCE.name(), "FIN_PAYABLES_PAID", event.amount(), 1L);
        }
    }

    // ------------------------------------------------------------------------
    // INVENTORY EVENT HANDLERS
    // ------------------------------------------------------------------------

    @Async
    @EventListener
    @Transactional
    public void onInventoryPurchaseCreated(InventoryPurchaseCreatedEvent event) {
        String eventKey = "INV_PURCHASE_" + event.purchaseId();
        if (!markEventProcessed(event.tenantId(), eventKey, "inventory.purchase.created")) {
            return;
        }

        AnalyticsInventoryProjection projection = AnalyticsInventoryProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .eventRefId(event.purchaseId())
                .movementType("PURCHASE")
                .productName(event.description() != null ? event.description() : "Entrada Estoque " + event.invoiceNumber())
                .quantity(BigDecimal.ONE)
                .unitPrice(event.totalAmount())
                .totalValue(event.totalAmount())
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .version(0L)
                .build();

        inventoryProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.INVENTORY.name(), "STOCK_PURCHASE_SPEND", event.totalAmount(), 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onStockConsumed(StockConsumedEvent event) {
        String eventKey = "STOCK_CONSUME_" + event.workOrderId() + "_" + (event.workOrderItemId() != null ? event.workOrderItemId() : event.productId());
        if (!markEventProcessed(event.tenantId(), eventKey, "inventory.stock.consumed")) {
            return;
        }

        AnalyticsInventoryProjection projection = AnalyticsInventoryProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .eventRefId(event.workOrderId())
                .movementType("WORK_ORDER_CONSUMPTION")
                .productId(event.productId())
                .productName(event.productName())
                .quantity(event.quantity())
                .unitPrice(event.unitCost() != null ? event.unitCost() : BigDecimal.ZERO)
                .totalValue(event.totalCost() != null ? event.totalCost() : BigDecimal.ZERO)
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .version(0L)
                .build();

        inventoryProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.INVENTORY.name(), "STOCK_CONSUMED_VALUE", event.totalCost(), 1L);
    }

    // ------------------------------------------------------------------------
    // TOOLS EVENT HANDLERS
    // ------------------------------------------------------------------------

    @Async
    @EventListener
    @Transactional
    public void onToolMaintenanceCompleted(ToolMaintenanceCompletedEvent event) {
        String eventKey = "TOOL_MAINT_" + event.maintenanceId();
        if (!markEventProcessed(event.tenantId(), eventKey, "tools.maintenance.completed")) {
            return;
        }

        AnalyticsToolProjection projection = AnalyticsToolProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .toolId(event.toolId())
                .eventType("MAINTENANCE_COMPLETED")
                .cost(event.cost() != null ? event.cost() : BigDecimal.ZERO)
                .downtimeHours(event.downtimeHours() != null ? event.downtimeHours() : BigDecimal.ZERO)
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .version(0L)
                .build();

        toolProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.TOOLS.name(), "TOOL_MAINTENANCE_COST", event.cost(), 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onToolCustodyAssigned(ToolCustodyAssignedEvent event) {
        AnalyticsToolProjection projection = AnalyticsToolProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .toolId(event.toolId())
                .eventType("CUSTODY_ASSIGNED")
                .cost(BigDecimal.ZERO)
                .downtimeHours(BigDecimal.ZERO)
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .version(0L)
                .build();

        toolProjectionRepository.save(projection);
        recordDailyMetric(event.tenantId(), event.unitId(), LocalDate.now(), KpiCategory.TOOLS.name(), "TOOL_ACTIVE_CUSTODY", BigDecimal.ONE, 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onToolCustodyReturned(ToolCustodyReturnedEvent event) {
        AnalyticsToolProjection projection = AnalyticsToolProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(event.tenantId())
                .unitId(event.unitId())
                .toolId(event.toolId())
                .eventType("CUSTODY_RETURNED")
                .cost(BigDecimal.ZERO)
                .downtimeHours(BigDecimal.ZERO)
                .occurredAt(event.occurredOn() != null ? event.occurredOn() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .version(0L)
                .build();

        toolProjectionRepository.save(projection);
    }

    // ------------------------------------------------------------------------
    // BILLING EVENT HANDLERS
    // ------------------------------------------------------------------------

    @Async
    @EventListener
    @Transactional
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        recordDailyMetric(event.tenantId(), null, LocalDate.now(), KpiCategory.BILLING.name(), "BILLING_PAYMENT_SUCCESS", event.amount(), 1L);
    }

    @Async
    @EventListener
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        recordDailyMetric(event.tenantId(), null, LocalDate.now(), KpiCategory.BILLING.name(), "BILLING_PAYMENT_FAILURE", BigDecimal.ONE, 1L);
    }

    // ------------------------------------------------------------------------
    // HELPER METHODS FOR REPLAY SAFETY & INCREMENTAL ROLLUPS
    // ------------------------------------------------------------------------

    private boolean markEventProcessed(UUID tenantId, String eventId, String eventType) {
        if (processedEventRepository.existsByTenantIdAndEventId(tenantId, eventId)) {
            log.info("Analytics consumer: evento {} ({}) já processado previamente para tenant {}", eventId, eventType, tenantId);
            return false;
        }

        AnalyticsProcessedEventLog processed = AnalyticsProcessedEventLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .eventId(eventId)
                .eventType(eventType)
                .processedAt(OffsetDateTime.now())
                .build();

        processedEventRepository.save(processed);
        return true;
    }

    private void recordDailyMetric(UUID tenantId, UUID unitId, LocalDate date, String dimension, String metricName, BigDecimal value, Long count) {
        if (value == null) value = BigDecimal.ZERO;
        if (count == null) count = 1L;

        Optional<AnalyticsDailyKpiSnapshot> existing = (unitId != null)
                ? dailySnapshotRepository.findByTenantIdAndUnitIdAndDateAndDimensionAndMetricName(tenantId, unitId, date, dimension, metricName)
                : dailySnapshotRepository.findByTenantIdAndUnitIdIsNullAndDateAndDimensionAndMetricName(tenantId, date, dimension, metricName);

        if (existing.isPresent()) {
            AnalyticsDailyKpiSnapshot snap = existing.get();
            snap.setMetricValue(snap.getMetricValue().add(value));
            snap.setMetricCount(snap.getMetricCount() + count);
            snap.setUpdatedAt(OffsetDateTime.now());
            dailySnapshotRepository.save(snap);
        } else {
            AnalyticsDailyKpiSnapshot newSnap = AnalyticsDailyKpiSnapshot.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .unitId(unitId)
                    .date(date)
                    .dimension(dimension)
                    .metricName(metricName)
                    .metricValue(value)
                    .metricCount(count)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .version(0L)
                    .build();
            dailySnapshotRepository.save(newSnap);
        }
    }
}
