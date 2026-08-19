package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentSummaryResponse;
import com.gomech.api.modules.operations.api.dto.ChangeAppointmentStatusRequest;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.api.dto.UpdateAppointmentRequest;
import com.gomech.api.modules.operations.domain.AppointmentNotFoundException;
import com.gomech.api.modules.operations.domain.AppointmentStatus;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.InvalidAppointmentStatusTransitionException;
import com.gomech.api.modules.operations.domain.InvalidCalendarRangeException;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Appointment;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CrmContract crmContract;

    @Mock
    private DomainEventBus domainEventBus;

    private AppointmentService appointmentService;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepository, crmContract, domainEventBus);
    }

    @Test
    @DisplayName("Deve agendar atendimento com sucesso quando associação CRM for válida")
    void shouldScheduleAppointmentSuccessfully() {
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime estimatedEndAt = scheduledAt.plusHours(2);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                unitId,
                customerId,
                vehicleId,
                scheduledAt,
                estimatedEndAt,
                "Revisão de 50.000 km",
                "Cliente relatou ruído nos freios"
        );

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(true);
        when(crmContract.findCustomerSummary(customerId, tenantId)).thenReturn(Optional.of(
                new CustomerSummaryResponse(customerId, "Ana Maria", "12345678909", "123.456.789-09", "(11) 9999-8888", "ana@email.com", 1, OffsetDateTime.now())
        ));
        when(crmContract.findVehicleSummary(vehicleId, tenantId)).thenReturn(Optional.of(
                new VehicleSummaryResponse(vehicleId, customerId, "Ana Maria", "BRA2E19", "BRA2E19", "Toyota", "Corolla", 2022, 48000)
        ));

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment app = inv.getArgument(0);
            app.setId(UUID.randomUUID());
            app.setCreatedAt(OffsetDateTime.now());
            app.setUpdatedAt(OffsetDateTime.now());
            return app;
        });

        AppointmentResponse response = appointmentService.scheduleAppointment(request, tenantId, unitId);

        assertThat(response.id()).isNotNull();
        assertThat(response.customerName()).isEqualTo("Ana Maria");
        assertThat(response.licensePlate()).isEqualTo("BRA2E19");
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve rejeitar agendamento quando veículo não pertencer ao cliente ou não existir no tenant")
    void shouldRejectAppointmentWhenCrmAssociationFails() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                unitId,
                customerId,
                vehicleId,
                OffsetDateTime.now().plusDays(1),
                null,
                "Troca de óleo",
                null
        );

        when(crmContract.validateCustomerAndVehicleAssociation(customerId, vehicleId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> appointmentService.scheduleAppointment(request, tenantId, unitId))
                .isInstanceOf(CustomerVehicleMismatchException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar agendamento quando data de término for anterior à data de início")
    void shouldRejectAppointmentWhenEndDateIsBeforeStartDate() {
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime invalidEndAt = scheduledAt.minusHours(1);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                unitId,
                customerId,
                vehicleId,
                scheduledAt,
                invalidEndAt,
                "Alinhamento",
                null
        );

        assertThatThrownBy(() -> appointmentService.scheduleAppointment(request, tenantId, unitId))
                .isInstanceOf(InvalidCalendarRangeException.class);
    }

    @Test
    @DisplayName("Deve alterar status de SCHEDULED para CONFIRMED com sucesso")
    void shouldChangeStatusFromScheduledToConfirmed() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setTenantId(tenantId);
        appointment.setUnitId(unitId);
        appointment.setCustomerId(customerId);
        appointment.setVehicleId(vehicleId);
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeAppointmentStatusRequest request = new ChangeAppointmentStatusRequest(AppointmentStatus.CONFIRMED, null);
        AppointmentResponse response = appointmentService.changeStatus(appointmentId, request, tenantId, unitId);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve cancelar agendamento com motivo e publicar evento de cancelamento")
    void shouldCancelAppointmentWithReason() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setTenantId(tenantId);
        appointment.setUnitId(unitId);
        appointment.setCustomerId(customerId);
        appointment.setVehicleId(vehicleId);
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        appointmentService.cancelAppointment(appointmentId, "Cliente solicitou cancelamento", tenantId, unitId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELED);
        assertThat(appointment.getCancellationReason()).isEqualTo("Cliente solicitou cancelamento");
        verify(domainEventBus, times(2)).publish(any()); // StatusChanged + Canceled
    }

    @Test
    @DisplayName("Deve consultar agendamentos por intervalo de calendário")
    void shouldGetCalendarAppointments() {
        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = from.plusDays(7);

        Appointment app1 = new Appointment();
        app1.setId(UUID.randomUUID());
        app1.setTenantId(tenantId);
        app1.setUnitId(unitId);
        app1.setCustomerId(customerId);
        app1.setVehicleId(vehicleId);
        app1.setScheduledAt(from.plusDays(1));
        app1.setStatus(AppointmentStatus.SCHEDULED);

        when(appointmentRepository.findByTenantIdAndUnitIdAndScheduledAtBetweenAndDeletedAtIsNullOrderByScheduledAtAsc(
                tenantId, unitId, from, to
        )).thenReturn(List.of(app1));

        List<AppointmentSummaryResponse> result = appointmentService.getCalendarAppointments(from, to, unitId, tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(app1.getId());
    }

    @Test
    @DisplayName("Deve buscar agendamentos com paginação")
    void shouldSearchAppointmentsWithPagination() {
        Appointment app1 = new Appointment();
        app1.setId(UUID.randomUUID());
        app1.setTenantId(tenantId);
        app1.setCustomerId(customerId);
        app1.setVehicleId(vehicleId);
        app1.setScheduledAt(OffsetDateTime.now());
        app1.setStatus(AppointmentStatus.SCHEDULED);

        Page<Appointment> page = new PageImpl<>(List.of(app1), PageRequest.of(0, 10), 1);
        when(appointmentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<AppointmentSummaryResponse> result = appointmentService.searchAppointments(AppointmentStatus.SCHEDULED, unitId, PageRequest.of(0, 10), tenantId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }
}
