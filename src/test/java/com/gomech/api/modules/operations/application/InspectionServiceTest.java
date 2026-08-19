package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEvent;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.CompleteInspectionRequest;
import com.gomech.api.modules.operations.api.dto.CreateInspectionRequest;
import com.gomech.api.modules.operations.api.dto.InspectionResponse;
import com.gomech.api.modules.operations.api.dto.SaveInspectionItemRequest;
import com.gomech.api.modules.operations.api.dto.UpdateInspectionRequest;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.FuelLevel;
import com.gomech.api.modules.operations.domain.InspectionAlreadyCompletedException;
import com.gomech.api.modules.operations.domain.InspectionCategory;
import com.gomech.api.modules.operations.domain.InspectionItemStatus;
import com.gomech.api.modules.operations.domain.InspectionNotFoundException;
import com.gomech.api.modules.operations.domain.InspectionStatus;
import com.gomech.api.modules.operations.domain.InvalidAppointmentInspectionLinkException;
import com.gomech.api.modules.operations.events.InspectionCanceledEvent;
import com.gomech.api.modules.operations.events.InspectionCompletedEvent;
import com.gomech.api.modules.operations.events.InspectionCreatedEvent;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Appointment;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import com.gomech.api.modules.operations.infrastructure.persistence.model.InspectionItem;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.AppointmentRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.InspectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CrmContract crmContract;

    @Mock
    private DomainEventBus domainEventBus;

    @InjectMocks
    private InspectionService inspectionService;

    private UUID tenantId;
    private UUID unitId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID appointmentId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Criar inspeção com sucesso e publicar evento")
    void createInspection_Success() {
        SaveInspectionItemRequest itemReq = new SaveInspectionItemRequest(
                null,
                InspectionCategory.BRAKES,
                "Pastilha dianteira",
                InspectionItemStatus.CRITICAL,
                "Desgastada abaixo do limite",
                "Substituir pastilhas e discos",
                "http://foto.com/pastilha.jpg"
        );

        CreateInspectionRequest request = new CreateInspectionRequest(
                unitId,
                customerId,
                vehicleId,
                null,
                FuelLevel.HALF,
                45000,
                "Veículo com ruído ao frear",
                List.of(itemReq)
        );

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(true);
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(new CustomerSummaryResponse(customerId, "João Silva", "12345678909", "123.456.789-09", "(11) 98888-7777", "joao@email.com", 1, java.time.OffsetDateTime.now())));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(new VehicleSummaryResponse(vehicleId, customerId, "João Silva", "ABC1D23", "ABC-1D23", "Toyota", "Corolla", 2022, 45000)));

        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> {
            Inspection saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InspectionResponse response = inspectionService.createInspection(request, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(InspectionStatus.IN_PROGRESS, response.status());
        assertEquals(FuelLevel.HALF, response.fuelLevel());
        assertEquals(45000, response.currentMileage());
        assertEquals(1, response.totalItems());
        assertEquals(1, response.criticalItems());
        assertEquals(0, response.okItems());

        verify(domainEventBus, times(1)).publish(any(InspectionCreatedEvent.class));
    }

    @Test
    @DisplayName("Criar inspeção associada a agendamento válido")
    void createInspection_WithAppointment_Success() {
        CreateInspectionRequest request = new CreateInspectionRequest(
                unitId,
                customerId,
                vehicleId,
                appointmentId,
                FuelLevel.FULL,
                50000,
                "Revisão agendada",
                List.of()
        );

        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setTenantId(tenantId);
        appointment.setUnitId(unitId);
        appointment.setVehicleId(vehicleId);

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(true);
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)).thenReturn(Optional.of(appointment));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(new CustomerSummaryResponse(customerId, "João Silva", "12345678909", "123.456.789-09", "(11) 98888-7777", "joao@email.com", 1, java.time.OffsetDateTime.now())));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(new VehicleSummaryResponse(vehicleId, customerId, "João Silva", "ABC1D23", "ABC-1D23", "Toyota", "Corolla", 2022, 45000)));

        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> {
            Inspection saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InspectionResponse response = inspectionService.createInspection(request, tenantId, unitId, userId);

        assertNotNull(response);
        assertEquals(appointmentId, response.appointmentId());
    }

    @Test
    @DisplayName("Falhar ao criar inspeção quando veículo não pertence ao cliente")
    void createInspection_CustomerVehicleMismatch_ThrowsException() {
        CreateInspectionRequest request = new CreateInspectionRequest(
                unitId,
                customerId,
                vehicleId,
                null,
                FuelLevel.FULL,
                50000,
                null,
                List.of()
        );

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(false);

        assertThrows(CustomerVehicleMismatchException.class,
                () -> inspectionService.createInspection(request, tenantId, unitId, userId));

        verify(inspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Falhar ao criar inspeção quando agendamento for de outro veículo")
    void createInspection_MismatchedAppointmentVehicle_ThrowsException() {
        CreateInspectionRequest request = new CreateInspectionRequest(
                unitId,
                customerId,
                vehicleId,
                appointmentId,
                FuelLevel.FULL,
                50000,
                null,
                List.of()
        );

        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setTenantId(tenantId);
        appointment.setUnitId(unitId);
        appointment.setVehicleId(UUID.randomUUID()); // Outro veículo

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(true);
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)).thenReturn(Optional.of(appointment));

        assertThrows(InvalidAppointmentInspectionLinkException.class,
                () -> inspectionService.createInspection(request, tenantId, unitId, userId));
    }

    @Test
    @DisplayName("Finalizar inspeção com sucesso e publicar evento")
    void completeInspection_Success() {
        UUID inspectionId = UUID.randomUUID();
        Inspection inspection = new Inspection();
        inspection.setId(inspectionId);
        inspection.setTenantId(tenantId);
        inspection.setUnitId(unitId);
        inspection.setCustomerId(customerId);
        inspection.setVehicleId(vehicleId);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);

        InspectionItem item = new InspectionItem();
        item.setCategory(InspectionCategory.TIRES);
        item.setName("Pneu Dianteiro");
        item.setStatus(InspectionItemStatus.ATTENTION);
        inspection.addItem(item);

        when(inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(inspectionId, tenantId)).thenReturn(Optional.of(inspection));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(new CustomerSummaryResponse(customerId, "João Silva", "12345678909", "123.456.789-09", "(11) 98888-7777", "joao@email.com", 1, java.time.OffsetDateTime.now())));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(new VehicleSummaryResponse(vehicleId, customerId, "João Silva", "ABC1D23", "ABC-1D23", "Toyota", "Corolla", 2022, 45000)));

        CompleteInspectionRequest completeRequest = new CompleteInspectionRequest("Vistoria finalizada", null);

        InspectionResponse response = inspectionService.completeInspection(inspectionId, completeRequest, tenantId);

        assertNotNull(response);
        assertEquals(InspectionStatus.COMPLETED, response.status());
        assertNotNull(response.completedAt());
        assertEquals(1, response.attentionItems());

        verify(domainEventBus, times(1)).publish(any(InspectionCompletedEvent.class));
    }

    @Test
    @DisplayName("Bloquear atualização de inspeção já finalizada")
    void updateInspection_WhenCompleted_ThrowsException() {
        UUID inspectionId = UUID.randomUUID();
        Inspection inspection = new Inspection();
        inspection.setId(inspectionId);
        inspection.setTenantId(tenantId);
        inspection.setStatus(InspectionStatus.COMPLETED);

        when(inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(inspectionId, tenantId)).thenReturn(Optional.of(inspection));

        UpdateInspectionRequest request = new UpdateInspectionRequest(FuelLevel.FULL, 60000, "Tentando editar");

        assertThrows(InspectionAlreadyCompletedException.class,
                () -> inspectionService.updateInspection(inspectionId, request, tenantId));
    }

    @Test
    @DisplayName("Cancelar inspeção com sucesso e publicar evento")
    void cancelInspection_Success() {
        UUID inspectionId = UUID.randomUUID();
        Inspection inspection = new Inspection();
        inspection.setId(inspectionId);
        inspection.setTenantId(tenantId);
        inspection.setUnitId(unitId);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);

        when(inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(inspectionId, tenantId)).thenReturn(Optional.of(inspection));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inspectionService.cancelInspection(inspectionId, tenantId);

        assertEquals(InspectionStatus.CANCELED, inspection.getStatus());
        assertNotNull(inspection.getDeletedAt());
        verify(domainEventBus, times(1)).publish(any(InspectionCanceledEvent.class));
    }
}
