package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCreatedEvent;
import com.gomech.api.modules.operations.events.WorkOrderStatusChangedEvent;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import com.gomech.api.modules.operations.infrastructure.persistence.model.QuoteItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrder;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.QuoteRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private CrmContract crmContract;

    @Mock
    private DomainEventBus eventBus;

    @InjectMocks
    private WorkOrderService workOrderService;

    private UUID tenantId;
    private UUID unitId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID mechanicId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        mechanicId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve criar ordem de serviço avulsa com peças e serviços e totais calculados")
    void shouldCreateWorkOrderSuccessfully() {
        SaveWorkOrderItemRequest item1 = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.PART,
                null,
                null,
                "Filtro de Óleo",
                "Substituição",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("45.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        SaveWorkOrderItemRequest item2 = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.SERVICE,
                null,
                mechanicId,
                "Troca de óleo e filtro",
                "Mão de obra",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("80.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO
        );

        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                unitId,
                customerId,
                vehicleId,
                null,
                mechanicId,
                "Box 01",
                45000,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2),
                "Serviço rápido",
                "Revisão de 45mil km",
                "Cliente aguarda no local",
                List.of(item1, item2)
        );

        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(
                new CustomerSummaryResponse(customerId, "Carlos Oliveira", "111.222.333-44", "111.222.333-44", "(11) 97777-8888", "carlos@email.com", 1, OffsetDateTime.now())
        ));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(
                new VehicleSummaryResponse(vehicleId, customerId, "Carlos Oliveira", "ABC1D23", "ABC-1D23", "Volkswagen", "Golf", 2020, 45000)
        ));

        WorkOrderResponse response = workOrderService.createWorkOrder(request, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(WorkOrderStatus.OPEN, response.status());
        assertEquals("Box 01", response.serviceBay());
        assertEquals(new BigDecimal("125.00"), response.subtotalAmount()); // 45 + 80
        assertEquals(new BigDecimal("5.00"), response.discountAmount());
        assertEquals(new BigDecimal("45.00"), response.totalPartsAmount());
        assertEquals(new BigDecimal("75.00"), response.totalServicesAmount());
        assertEquals(new BigDecimal("120.00"), response.totalAmount());
        assertEquals(2, response.items().size());

        verify(eventBus).publish(any(WorkOrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Deve converter orçamento aprovado em ordem de serviço atomicamente")
    void shouldConvertApprovedQuoteToWorkOrder() {
        UUID quoteId = UUID.randomUUID();
        Quote quote = new Quote();
        quote.setId(quoteId);
        quote.setTenantId(tenantId);
        quote.setUnitId(unitId);
        quote.setCustomerId(customerId);
        quote.setVehicleId(vehicleId);
        quote.setStatus(QuoteStatus.CUSTOMER_APPROVED);
        quote.setCustomerApprovalStatus(CustomerApprovalStatus.APPROVED);
        quote.setNotes("Orçamento de freios aprovado");

        QuoteItem qItem = new QuoteItem(
                quote,
                tenantId,
                QuoteItemType.PART,
                null,
                "Pastilha Dianteira",
                "Peça genuína",
                new BigDecimal("2.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("300.00")
        );
        quote.addItem(qItem);

        when(quoteRepository.findByIdWithItems(quoteId, tenantId)).thenReturn(Optional.of(quote));
        when(workOrderRepository.findByTenantIdAndQuoteIdAndDeletedAtIsNull(tenantId, quoteId)).thenReturn(Optional.empty());
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderResponse response = workOrderService.createFromQuote(quoteId, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(WorkOrderStatus.OPEN, response.status());
        assertEquals(quoteId, response.quoteId());
        assertEquals(new BigDecimal("300.00"), response.totalAmount());
        assertEquals(1, response.items().size());

        verify(eventBus).publish(any(WorkOrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Deve rejeitar conversão de orçamento que não foi aprovado pelo cliente")
    void shouldRejectConversionOfUnapprovedQuote() {
        UUID quoteId = UUID.randomUUID();
        Quote quote = new Quote();
        quote.setId(quoteId);
        quote.setTenantId(tenantId);
        quote.setStatus(QuoteStatus.PENDING_INTERNAL_APPROVAL);
        quote.setCustomerApprovalStatus(CustomerApprovalStatus.PENDING);

        when(quoteRepository.findByIdWithItems(quoteId, tenantId)).thenReturn(Optional.of(quote));

        assertThrows(QuoteNotEligibleForWorkOrderException.class, () ->
                workOrderService.createFromQuote(quoteId, tenantId, unitId, userId)
        );
    }

    @Test
    @DisplayName("Deve rejeitar conversão duplicada do mesmo orçamento")
    void shouldRejectDuplicateQuoteConversion() {
        UUID quoteId = UUID.randomUUID();
        Quote quote = new Quote();
        quote.setId(quoteId);
        quote.setTenantId(tenantId);
        quote.setStatus(QuoteStatus.CUSTOMER_APPROVED);
        quote.setCustomerApprovalStatus(CustomerApprovalStatus.APPROVED);

        WorkOrder existing = new WorkOrder();
        existing.setId(UUID.randomUUID());

        when(quoteRepository.findByIdWithItems(quoteId, tenantId)).thenReturn(Optional.of(quote));
        when(workOrderRepository.findByTenantIdAndQuoteIdAndDeletedAtIsNull(tenantId, quoteId)).thenReturn(Optional.of(existing));

        assertThrows(QuoteAlreadyConvertedException.class, () ->
                workOrderService.createFromQuote(quoteId, tenantId, unitId, userId)
        );
    }

    @Test
    @DisplayName("Deve transicionar status e finalizar ordem de serviço publicando WorkOrderCompletedEvent")
    void shouldCompleteWorkOrderAndPublishCompletionEvent() {
        UUID orderId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(orderId);
        workOrder.setTenantId(tenantId);
        workOrder.setUnitId(unitId);
        workOrder.setCustomerId(customerId);
        workOrder.setVehicleId(vehicleId);
        workOrder.setOrderNumber("OS-12345");
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setTotalAmount(new BigDecimal("500.00"));
        workOrder.setTotalPartsAmount(new BigDecimal("300.00"));
        workOrder.setTotalServicesAmount(new BigDecimal("200.00"));
        workOrder.setItems(new ArrayList<>());

        when(workOrderRepository.findByIdWithItems(orderId, tenantId)).thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CompleteWorkOrderRequest completeReq = new CompleteWorkOrderRequest(
                50500,
                "Serviço finalizado com teste de rodagem ok.",
                "Veículo pronto para retirada."
        );

        WorkOrderResponse response = workOrderService.completeWorkOrder(orderId, completeReq, tenantId);

        assertNotNull(response);
        assertEquals(WorkOrderStatus.COMPLETED, response.status());
        assertEquals(50500, response.endMileage());
        assertNotNull(response.completedAt());

        verify(eventBus).publish(any(WorkOrderCompletedEvent.class));
        verify(eventBus).publish(any(WorkOrderStatusChangedEvent.class));
    }
}
