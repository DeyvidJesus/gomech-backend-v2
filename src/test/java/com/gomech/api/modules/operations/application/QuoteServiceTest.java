package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import com.gomech.api.modules.operations.infrastructure.persistence.model.InspectionItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.AppointmentRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.InspectionRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CrmContract crmContract;

    @Mock
    private DomainEventBus eventBus;

    private QuoteService quoteService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(
                quoteRepository,
                inspectionRepository,
                appointmentRepository,
                crmContract,
                eventBus
        );
    }

    @Test
    @DisplayName("Deve criar orçamento avulso com itens e calcular totais com sucesso")
    void shouldCreateQuoteWithItemsSuccessfully() {
        SaveQuoteItemRequest item1 = new SaveQuoteItemRequest(
                null,
                QuoteItemType.PART,
                null,
                "Pastilha de Freio",
                "Substituição",
                new BigDecimal("2.00"),
                new BigDecimal("120.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO
        );

        SaveQuoteItemRequest item2 = new SaveQuoteItemRequest(
                null,
                QuoteItemType.LABOR,
                null,
                "Mão de obra troca de pastilha",
                "Serviço",
                new BigDecimal("1.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00")
        );

        CreateQuoteRequest request = new CreateQuoteRequest(
                unitId,
                customerId,
                vehicleId,
                null,
                null,
                OffsetDateTime.now().plusDays(10),
                "Observações",
                "Termos",
                List.of(item1, item2)
        );

        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(
                new CustomerSummaryResponse(customerId, "João Silva", "123.456.789-00", "123.456.789-00", "(11) 98888-7777", "joao@email.com", 1, OffsetDateTime.now())
        ));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(
                new VehicleSummaryResponse(vehicleId, customerId, "João Silva", "ABC1D23", "ABC1D23", "Toyota", "Corolla", 2022, 45000)
        ));

        QuoteResponse response = quoteService.createQuote(request, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(QuoteStatus.DRAFT, response.status());
        assertEquals(CustomerApprovalStatus.PENDING, response.customerApprovalStatus());
        assertEquals(new BigDecimal("340.00"), response.subtotalAmount()); // 240 + 100
        assertEquals(new BigDecimal("10.00"), response.discountAmount());
        assertEquals(new BigDecimal("5.00"), response.taxAmount());
        assertEquals(new BigDecimal("230.00"), response.totalPartsAmount()); // 240 - 10
        assertEquals(new BigDecimal("105.00"), response.totalLaborAmount()); // 100 + 5
        assertEquals(new BigDecimal("335.00"), response.totalAmount()); // 340 - 10 + 5
        assertEquals(2, response.items().size());

        verify(crmContract).validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId);
        verify(eventBus).publish(any());
    }

    @Test
    @DisplayName("Deve gerar orçamento a partir de vistoria técnica com itens em atenção e críticos")
    void shouldCreateQuoteFromInspection() {
        UUID inspectionId = UUID.randomUUID();
        Inspection inspection = new Inspection();
        inspection.setId(inspectionId);
        inspection.setTenantId(tenantId);
        inspection.setUnitId(unitId);
        inspection.setCustomerId(customerId);
        inspection.setVehicleId(vehicleId);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setFuelLevel(FuelLevel.HALF);
        inspection.setCurrentMileage(50000);
        inspection.setGeneralNotes("Vistoria com problemas");

        InspectionItem item1 = new InspectionItem();
        item1.setId(UUID.randomUUID());
        item1.setInspection(inspection);
        item1.setTenantId(tenantId);
        item1.setCategory(InspectionCategory.BRAKES);
        item1.setName("Disco de Freio");
        item1.setStatus(InspectionItemStatus.CRITICAL);
        item1.setNotes("Desgaste excessivo");
        item1.setRecommendedAction("Substituição imediata dos discos");

        InspectionItem item2 = new InspectionItem();
        item2.setId(UUID.randomUUID());
        item2.setInspection(inspection);
        item2.setTenantId(tenantId);
        item2.setCategory(InspectionCategory.FLUIDS);
        item2.setName("Óleo do Motor");
        item2.setStatus(InspectionItemStatus.OK);
        item2.setNotes("Nível adequado");

        InspectionItem item3 = new InspectionItem();
        item3.setId(UUID.randomUUID());
        item3.setInspection(inspection);
        item3.setTenantId(tenantId);
        item3.setCategory(InspectionCategory.TIRES);
        item3.setName("Pneu Dianteiro Direito");
        item3.setStatus(InspectionItemStatus.ATTENTION);
        item3.setNotes("Meia vida");
        item3.setRecommendedAction("Calibragem e alinhamento");

        inspection.setItems(new java.util.ArrayList<>(List.of(item1, item2, item3)));

        when(inspectionRepository.findByIdWithItems(inspectionId, tenantId)).thenReturn(Optional.of(inspection));
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuoteResponse response = quoteService.createQuoteFromInspection(inspectionId, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(QuoteStatus.DRAFT, response.status());
        assertEquals(2, response.items().size()); // Apenas itens CRITICAL e ATTENTION

        verify(eventBus).publish(any());
    }

    @Test
    @DisplayName("Deve executar o fluxo completo de dupla aprovação com sucesso")
    void shouldExecuteDualApprovalWorkflowSuccessfully() {
        Quote quote = new Quote(tenantId, unitId, customerId, vehicleId, null, null, userId, null, null, null);
        UUID quoteId = UUID.randomUUID();

        when(quoteRepository.findByIdWithItems(quoteId, tenantId)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Submeter para aprovação interna
        quoteService.submitForInternalApproval(quoteId, tenantId);
        assertEquals(QuoteStatus.PENDING_INTERNAL_APPROVAL, quote.getStatus());

        // 2. Aprovar internamente (Gerente)
        UUID approverId = UUID.randomUUID();
        quoteService.approveInternally(quoteId, approverId, tenantId);
        assertEquals(QuoteStatus.INTERNAL_APPROVED, quote.getStatus());
        assertEquals(approverId, quote.getApprovedByUserId());
        assertNotNull(quote.getApprovedAt());

        // 3. Enviar ao cliente
        quoteService.sendToCustomer(quoteId, tenantId);
        assertEquals(QuoteStatus.SENT_TO_CUSTOMER, quote.getStatus());

        // 4. Decisão do cliente (Aprovado)
        CustomerDecisionRequest decision = new CustomerDecisionRequest(true, "Aprovado pelo WhatsApp");
        quoteService.processCustomerDecision(quoteId, decision, tenantId);

        assertEquals(QuoteStatus.CUSTOMER_APPROVED, quote.getStatus());
        assertEquals(CustomerApprovalStatus.APPROVED, quote.getCustomerApprovalStatus());
        assertNotNull(quote.getCustomerDecisionAt());
        assertEquals("Aprovado pelo WhatsApp", quote.getCustomerDecisionNotes());
    }

    @Test
    @DisplayName("Deve bloquear envio ao cliente se não estiver aprovado internamente")
    void shouldBlockSendingToCustomerWithoutInternalApproval() {
        Quote quote = new Quote(tenantId, unitId, customerId, vehicleId, null, null, userId, null, null, null);
        quote.setStatus(QuoteStatus.DRAFT);
        UUID quoteId = UUID.randomUUID();

        when(quoteRepository.findByIdWithItems(quoteId, tenantId)).thenReturn(Optional.of(quote));

        assertThrows(QuoteNotApprovedForSendingException.class, () ->
                quoteService.sendToCustomer(quoteId, tenantId)
        );
    }
}
