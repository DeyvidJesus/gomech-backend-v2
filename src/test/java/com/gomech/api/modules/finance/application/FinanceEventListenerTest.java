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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceEventListenerTest {

    @Mock
    private ReceivableService receivableService;

    @Mock
    private PayableService payableService;

    @Mock
    private FinanceCategoryService categoryService;

    @InjectMocks
    private FinanceEventListener eventListener;

    private UUID tenantId;
    private UUID unitId;
    private UUID workOrderId;
    private FinanceCategory category;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        workOrderId = UUID.randomUUID();

        category = FinanceCategory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Serviços")
                .type(TransactionType.CREDIT)
                .dreCategoryType(DreCategoryType.GROSS_REVENUE)
                .build();
    }

    @Test
    @DisplayName("Should create receivable on WorkOrderCompletedEvent")
    void shouldCreateReceivableOnWorkOrderCompleted() {
        WorkOrderCompletedEvent event = new WorkOrderCompletedEvent(
                workOrderId,
                tenantId,
                unitId,
                "OS-001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(2500.00),
                BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(1500.00),
                OffsetDateTime.now(),
                50000,
                3,
                OffsetDateTime.now()
        );

        when(categoryService.getOrCreateDefaultCategory(eq(tenantId), any(), any(), any())).thenReturn(category);

        eventListener.onWorkOrderCompleted(event);

        verify(receivableService).createReceivable(any(ReceivableDtos.Create.class), eq(tenantId));
    }

    @Test
    @DisplayName("Should reverse receivable on WorkOrderReopenedEvent")
    void shouldReverseReceivableOnWorkOrderReopened() {
        WorkOrderReopenedEvent event = new WorkOrderReopenedEvent(workOrderId, tenantId, unitId, "Garantia");

        eventListener.onWorkOrderReopened(event);

        verify(receivableService).reverseReceivableForWorkOrder(eq(workOrderId), any(), eq(tenantId));
    }

    @Test
    @DisplayName("Should reverse receivable on WorkOrderCanceledEvent")
    void shouldReverseReceivableOnWorkOrderCanceled() {
        WorkOrderCanceledEvent event = new WorkOrderCanceledEvent(workOrderId, tenantId, unitId, "Desistência");

        eventListener.onWorkOrderCanceled(event);

        verify(receivableService).reverseReceivableForWorkOrder(eq(workOrderId), any(), eq(tenantId));
    }

    @Test
    @DisplayName("Should create payable on InventoryPurchaseCreatedEvent")
    void shouldCreatePayableOnInventoryPurchaseCreated() {
        UUID purchaseId = UUID.randomUUID();
        InventoryPurchaseCreatedEvent event = new InventoryPurchaseCreatedEvent(
                purchaseId,
                tenantId,
                unitId,
                "Auto Peças São Paulo",
                "NF-889900",
                BigDecimal.valueOf(4500.00),
                LocalDate.now().plusDays(30),
                "Filtros e Óleo 5W30"
        );

        when(categoryService.getOrCreateDefaultCategory(eq(tenantId), any(), any(), any())).thenReturn(category);

        eventListener.onInventoryPurchaseCreated(event);

        verify(payableService).createPayable(any(PayableDtos.Create.class), eq(tenantId));
    }
}
