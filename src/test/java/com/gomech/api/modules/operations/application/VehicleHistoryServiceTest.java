package com.gomech.api.modules.operations.application;

import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryExportResponse;
import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryResponse;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import com.gomech.api.modules.operations.infrastructure.persistence.model.InspectionItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrder;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrderItem;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.InspectionRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleHistoryServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private CrmContract crmContract;

    @InjectMocks
    private VehicleHistoryService vehicleHistoryService;

    private UUID tenantId;
    private UUID vehicleId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve agregar histórico completo de manutenções e calcular métricas com precisão")
    void shouldAggregateVehicleServiceHistoryAndCalculateMetricsSuccessfully() {
        VehicleSummaryResponse vehicle = new VehicleSummaryResponse(
                vehicleId, customerId, "Ana Silva", "ABC1D23", "ABC-1D23", "Honda", "Civic", 2021, 45000
        );

        CustomerSummaryResponse customer = new CustomerSummaryResponse(
                customerId, "Ana Silva", "123.456.789-00", "123.456.789-00", "(11) 98888-1111", "ana@email.com", 1, OffsetDateTime.now()
        );

        // OS 1: 40.000 km, R$ 300,00 (1 peça de R$ 100, 1 serviço de R$ 200)
        WorkOrder wo1 = new WorkOrder();
        wo1.setId(UUID.randomUUID());
        wo1.setOrderNumber("OS-001");
        wo1.setStatus(WorkOrderStatus.COMPLETED);
        wo1.setCompletedAt(OffsetDateTime.now().minusMonths(6));
        wo1.setStartMileage(40000);
        wo1.setEndMileage(40010);
        wo1.setTotalAmount(new BigDecimal("300.00"));
        wo1.setTotalPartsAmount(new BigDecimal("100.00"));
        wo1.setTotalServicesAmount(new BigDecimal("200.00"));

        WorkOrderItem wo1Item1 = new WorkOrderItem(wo1, tenantId, WorkOrderItemType.PART, null, null, "Filtro de Óleo", null, WorkOrderItemStatus.COMPLETED, new BigDecimal("1.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"));
        WorkOrderItem wo1Item2 = new WorkOrderItem(wo1, tenantId, WorkOrderItemType.SERVICE, null, null, "Troca de Óleo", null, WorkOrderItemStatus.COMPLETED, new BigDecimal("1.00"), new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.00"));
        wo1.setItems(List.of(wo1Item1, wo1Item2));

        // OS 2: 45.000 km, R$ 500,00 (2 pastilhas de R$ 150 = 300, 1 serviço de R$ 200)
        WorkOrder wo2 = new WorkOrder();
        wo2.setId(UUID.randomUUID());
        wo2.setOrderNumber("OS-002");
        wo2.setStatus(WorkOrderStatus.COMPLETED);
        wo2.setCompletedAt(OffsetDateTime.now().minusDays(10));
        wo2.setStartMileage(45000);
        wo2.setEndMileage(45020);
        wo2.setTotalAmount(new BigDecimal("500.00"));
        wo2.setTotalPartsAmount(new BigDecimal("300.00"));
        wo2.setTotalServicesAmount(new BigDecimal("200.00"));

        WorkOrderItem wo2Item1 = new WorkOrderItem(wo2, tenantId, WorkOrderItemType.PART, null, null, "Pastilha de Freio", null, WorkOrderItemStatus.COMPLETED, new BigDecimal("2.00"), new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"));
        WorkOrderItem wo2Item2 = new WorkOrderItem(wo2, tenantId, WorkOrderItemType.SERVICE, null, null, "Troca de Pastilhas", null, WorkOrderItemStatus.COMPLETED, new BigDecimal("1.00"), new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.00"));
        wo2.setItems(List.of(wo2Item1, wo2Item2));

        // Vistoria Concluída
        Inspection insp = new Inspection();
        insp.setId(UUID.randomUUID());
        insp.setTenantId(tenantId);
        insp.setCustomerId(customerId);
        insp.setVehicleId(vehicleId);
        insp.setFuelLevel(FuelLevel.HALF);
        insp.setCurrentMileage(45000);
        insp.setGeneralNotes("Revisão geral");
        insp.setStatus(InspectionStatus.COMPLETED);

        InspectionItem inspItem1 = new InspectionItem();
        inspItem1.setId(UUID.randomUUID());
        inspItem1.setInspection(insp);
        inspItem1.setTenantId(tenantId);
        inspItem1.setCategory(InspectionCategory.BRAKES);
        inspItem1.setName("Pastilhas");
        inspItem1.setStatus(InspectionItemStatus.ATTENTION);
        inspItem1.setNotes("Desgaste moderado");
        inspItem1.setRecommendedAction("Trocar em breve");
        insp.setItems(List.of(inspItem1));

        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(customer));
        when(workOrderRepository.findCompletedByVehicleWithItems(tenantId, vehicleId, WorkOrderStatus.COMPLETED))
                .thenReturn(List.of(wo2, wo1));
        when(inspectionRepository.findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, vehicleId))
                .thenReturn(List.of(insp));

        VehicleServiceHistoryResponse response = vehicleHistoryService.getVehicleServiceHistory(vehicleId, tenantId);

        assertNotNull(response);
        assertEquals(vehicleId, response.vehicleId());
        assertEquals("ABC1D23", response.licensePlate());
        assertEquals("Ana Silva", response.customer().name());

        // Métricas
        assertEquals(2, response.metrics().totalServicesCount());
        assertEquals(new BigDecimal("800.00"), response.metrics().totalSpent()); // 300 + 500
        assertEquals(new BigDecimal("400.00"), response.metrics().averageTicket()); // 800 / 2
        assertEquals(45020, response.metrics().lastRecordedMileage());
        assertEquals(3, response.metrics().totalPartsReplacedCount()); // 1 + 2

        // Linha do tempo
        assertEquals(2, response.workOrders().size());
        assertEquals("OS-002", response.workOrders().get(0).orderNumber());
        assertEquals("OS-001", response.workOrders().get(1).orderNumber());
        assertEquals(1, response.inspections().size());
    }

    @Test
    @DisplayName("Deve lançar exceção quando o veículo não existir no tenant")
    void shouldThrowWhenVehicleNotFound() {
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () ->
                vehicleHistoryService.getVehicleServiceHistory(vehicleId, tenantId)
        );
    }

    @Test
    @DisplayName("Deve gerar dossiê exportável com código de autenticidade e garantias")
    void shouldGenerateExportableDossierSuccessfully() {
        VehicleSummaryResponse vehicle = new VehicleSummaryResponse(
                vehicleId, customerId, "Carlos Souza", "XYZ9K88", "XYZ-9K88", "Toyota", "Corolla", 2022, 50000
        );

        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.empty());
        when(workOrderRepository.findCompletedByVehicleWithItems(tenantId, vehicleId, WorkOrderStatus.COMPLETED))
                .thenReturn(List.of());
        when(inspectionRepository.findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, vehicleId))
                .thenReturn(List.of());

        VehicleServiceHistoryExportResponse exportResponse = vehicleHistoryService.getVehicleServiceHistoryExport(vehicleId, tenantId);

        assertNotNull(exportResponse);
        assertTrue(exportResponse.reportId().startsWith("DOSSIER-XYZ9K88-"));
        assertTrue(exportResponse.authenticityVerificationCode().startsWith("GM-AUTH-"));
        assertNotNull(exportResponse.termsAndWarrantyNotice());
        assertEquals(0, exportResponse.metrics().totalServicesCount());
    }
}
