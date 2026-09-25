package com.gomech.api.modules.analytics.application;

import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsDailyKpiSnapshot;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsFinancialProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsInventoryProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsProcessedEventLog;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsWorkOrderProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsDailyKpiSnapshotRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsFinancialProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsInventoryProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsProcessedEventLogRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsToolProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsWorkOrderProjectionRepository;
import com.gomech.api.modules.finance.events.ReceivableCreatedEvent;
import com.gomech.api.modules.inventory.events.InventoryPurchaseCreatedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventListenerTest {

    @Mock
    private AnalyticsProcessedEventLogRepository processedEventRepository;

    @Mock
    private AnalyticsDailyKpiSnapshotRepository dailySnapshotRepository;

    @Mock
    private AnalyticsWorkOrderProjectionRepository workOrderProjectionRepository;

    @Mock
    private AnalyticsFinancialProjectionRepository financialProjectionRepository;

    @Mock
    private AnalyticsInventoryProjectionRepository inventoryProjectionRepository;

    @Mock
    private AnalyticsToolProjectionRepository toolProjectionRepository;

    @InjectMocks
    private AnalyticsEventListener listener;

    private UUID tenantId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should process WorkOrderCreatedEvent and record daily metric")
    void shouldProcessWorkOrderCreatedEvent() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderCreatedEvent event = new WorkOrderCreatedEvent(
                workOrderId, tenantId, unitId, "OS-1001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(350.00)
        );

        when(processedEventRepository.existsByTenantIdAndEventId(tenantId, "WO_CREATED_" + workOrderId)).thenReturn(false);

        listener.onWorkOrderCreated(event);

        verify(processedEventRepository).save(any(AnalyticsProcessedEventLog.class));
        verify(workOrderProjectionRepository).save(any(AnalyticsWorkOrderProjection.class));
        verify(dailySnapshotRepository).save(any(AnalyticsDailyKpiSnapshot.class));
    }

    @Test
    @DisplayName("Should skip duplicate event for replay safety")
    void shouldSkipDuplicateEventForReplaySafety() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderCreatedEvent event = new WorkOrderCreatedEvent(
                workOrderId, tenantId, unitId, "OS-1001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(350.00)
        );

        when(processedEventRepository.existsByTenantIdAndEventId(tenantId, "WO_CREATED_" + workOrderId)).thenReturn(true);

        listener.onWorkOrderCreated(event);

        verify(workOrderProjectionRepository, never()).save(any());
        verify(dailySnapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should materialize completed work order with turnaround time calculation")
    void shouldMaterializeCompletedWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        OffsetDateTime openedAt = OffsetDateTime.now().minusHours(3);
        OffsetDateTime completedAt = OffsetDateTime.now();

        AnalyticsWorkOrderProjection existing = AnalyticsWorkOrderProjection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .workOrderId(workOrderId)
                .status("CREATED")
                .openedAt(openedAt)
                .build();

        when(processedEventRepository.existsByTenantIdAndEventId(tenantId, "WO_COMPLETED_" + workOrderId)).thenReturn(false);
        when(workOrderProjectionRepository.findByTenantIdAndWorkOrderId(tenantId, workOrderId)).thenReturn(Optional.of(existing));

        WorkOrderCompletedEvent event = new WorkOrderCompletedEvent(
                workOrderId, tenantId, unitId, "OS-1001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.valueOf(600.00), BigDecimal.valueOf(200.00), BigDecimal.valueOf(400.00),
                completedAt, 45000, 3
        );

        listener.onWorkOrderCompleted(event);

        verify(workOrderProjectionRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo("COMPLETED");
        assertThat(existing.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600.00));
        assertThat(existing.getTurnaroundHours()).isNotNull();
    }

    @Test
    @DisplayName("Should process Financial and Inventory events properly")
    void shouldProcessFinancialAndInventoryEvents() {
        UUID recId = UUID.randomUUID();
        ReceivableCreatedEvent recEvent = new ReceivableCreatedEvent(
                recId, tenantId, unitId, UUID.randomUUID(), UUID.randomUUID(), "OS-1001", "Serviço OS", BigDecimal.valueOf(500.00), LocalDate.now().plusDays(15), "PENDING"
        );

        when(processedEventRepository.existsByTenantIdAndEventId(tenantId, "FIN_REC_CREATED_" + recId)).thenReturn(false);

        listener.onReceivableCreated(recEvent);
        verify(financialProjectionRepository).save(any(AnalyticsFinancialProjection.class));

        UUID purchaseId = UUID.randomUUID();
        InventoryPurchaseCreatedEvent invEvent = new InventoryPurchaseCreatedEvent(
                purchaseId, tenantId, unitId, "Distribuidora Auto", "NF-999", BigDecimal.valueOf(1200.00), LocalDate.now().plusDays(30), "Compra Óleo e Pastilhas"
        );

        when(processedEventRepository.existsByTenantIdAndEventId(tenantId, "INV_PURCHASE_" + purchaseId)).thenReturn(false);

        listener.onInventoryPurchaseCreated(invEvent);
        verify(inventoryProjectionRepository).save(any(AnalyticsInventoryProjection.class));
    }
}
