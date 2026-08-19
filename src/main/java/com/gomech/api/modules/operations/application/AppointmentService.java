package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentSummaryResponse;
import com.gomech.api.modules.operations.api.dto.ChangeAppointmentStatusRequest;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.api.dto.UpdateAppointmentRequest;
import com.gomech.api.modules.operations.domain.AppointmentLifecycleValidator;
import com.gomech.api.modules.operations.domain.AppointmentNotFoundException;
import com.gomech.api.modules.operations.domain.AppointmentStatus;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.InvalidAppointmentStatusTransitionException;
import com.gomech.api.modules.operations.domain.InvalidCalendarRangeException;
import com.gomech.api.modules.operations.events.AppointmentCanceledEvent;
import com.gomech.api.modules.operations.events.AppointmentScheduledEvent;
import com.gomech.api.modules.operations.events.AppointmentStatusChangedEvent;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Appointment;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.AppointmentRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CrmContract crmContract;
    private final DomainEventBus domainEventBus;

    @Transactional
    public AppointmentResponse scheduleAppointment(CreateAppointmentRequest request, UUID tenantId, UUID unitId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);
        UUID effectiveUnitId = request.unitId() != null ? request.unitId() : unitId;

        if (request.estimatedEndAt() != null && request.scheduledAt().isAfter(request.estimatedEndAt())) {
            throw new InvalidCalendarRangeException(request.scheduledAt(), request.estimatedEndAt());
        }

        // Validação da associação Cliente x Veículo através do contrato explícito do CRM
        boolean validAssociation = crmContract.validateCustomerAndVehicleAssociation(
                request.customerId(), request.vehicleId(), effectiveTenantId
        );
        if (!validAssociation) {
            throw new CustomerVehicleMismatchException(request.customerId(), request.vehicleId());
        }

        Appointment appointment = new Appointment();
        appointment.setTenantId(effectiveTenantId);
        appointment.setUnitId(effectiveUnitId);
        appointment.setCustomerId(request.customerId());
        appointment.setVehicleId(request.vehicleId());
        appointment.setScheduledAt(request.scheduledAt());
        appointment.setEstimatedEndAt(request.estimatedEndAt());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setServiceType(request.serviceType() != null ? request.serviceType().trim() : null);
        appointment.setNotes(request.notes() != null ? request.notes().trim() : null);

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Agendamento {} criado com sucesso para o cliente {} e veículo {} na unidade {}",
                saved.getId(), saved.getCustomerId(), saved.getVehicleId(), effectiveUnitId);

        domainEventBus.publish(new AppointmentScheduledEvent(
                effectiveTenantId,
                effectiveUnitId,
                saved.getId(),
                saved.getCustomerId(),
                saved.getVehicleId(),
                saved.getScheduledAt(),
                saved.getServiceType()
        ));

        return toAppointmentResponse(saved, effectiveTenantId);
    }

    @Transactional
    public AppointmentResponse updateAppointment(UUID id, UpdateAppointmentRequest request, UUID tenantId, UUID unitId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        if (appointment.getStatus().isTerminal()) {
            throw new InvalidAppointmentStatusTransitionException(appointment.getStatus(), appointment.getStatus());
        }

        if (request.estimatedEndAt() != null && request.scheduledAt().isAfter(request.estimatedEndAt())) {
            throw new InvalidCalendarRangeException(request.scheduledAt(), request.estimatedEndAt());
        }

        appointment.setScheduledAt(request.scheduledAt());
        appointment.setEstimatedEndAt(request.estimatedEndAt());
        appointment.setServiceType(request.serviceType() != null ? request.serviceType().trim() : null);
        appointment.setNotes(request.notes() != null ? request.notes().trim() : null);

        Appointment updated = appointmentRepository.save(appointment);
        log.info("Agendamento {} atualizado com sucesso no tenant {}", updated.getId(), effectiveTenantId);

        return toAppointmentResponse(updated, effectiveTenantId);
    }

    @Transactional
    public AppointmentResponse changeStatus(UUID id, ChangeAppointmentStatusRequest request, UUID tenantId, UUID unitId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        AppointmentStatus previousStatus = appointment.getStatus();
        AppointmentStatus targetStatus = request.status();

        AppointmentLifecycleValidator.validateTransition(previousStatus, targetStatus);

        appointment.setStatus(targetStatus);
        if (targetStatus == AppointmentStatus.CANCELED && request.cancellationReason() != null) {
            appointment.setCancellationReason(request.cancellationReason().trim());
        }

        Appointment updated = appointmentRepository.save(appointment);
        log.info("Status do agendamento {} alterado de {} para {} no tenant {}",
                id, previousStatus, targetStatus, effectiveTenantId);

        domainEventBus.publish(new AppointmentStatusChangedEvent(
                effectiveTenantId,
                updated.getUnitId(),
                updated.getId(),
                previousStatus,
                targetStatus
        ));

        if (targetStatus == AppointmentStatus.CANCELED) {
            domainEventBus.publish(new AppointmentCanceledEvent(
                    effectiveTenantId,
                    updated.getUnitId(),
                    updated.getId(),
                    appointment.getCancellationReason()
            ));
        }

        return toAppointmentResponse(updated, effectiveTenantId);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(UUID id, UUID tenantId, UUID unitId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        return toAppointmentResponse(appointment, effectiveTenantId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentSummaryResponse> getCalendarAppointments(
            OffsetDateTime from,
            OffsetDateTime to,
            UUID unitId,
            UUID tenantId
    ) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        if (from == null || to == null) {
            throw new InvalidCalendarRangeException("As datas inicial (from) e final (to) são obrigatórias para consulta de calendário.");
        }

        if (from.isAfter(to)) {
            throw new InvalidCalendarRangeException(from, to);
        }

        List<Appointment> appointments;
        if (unitId != null) {
            appointments = appointmentRepository.findByTenantIdAndUnitIdAndScheduledAtBetweenAndDeletedAtIsNullOrderByScheduledAtAsc(
                    effectiveTenantId, unitId, from, to
            );
        } else {
            appointments = appointmentRepository.findByTenantIdAndScheduledAtBetweenAndDeletedAtIsNullOrderByScheduledAtAsc(
                    effectiveTenantId, from, to
            );
        }

        return appointments.stream()
                .map(app -> toSummaryResponse(app, effectiveTenantId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AppointmentSummaryResponse> searchAppointments(
            AppointmentStatus status,
            UUID unitId,
            Pageable pageable,
            UUID tenantId
    ) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), effectiveTenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Appointment> page = appointmentRepository.findAll(spec, pageable);
        return page.map(app -> toSummaryResponse(app, effectiveTenantId));
    }

    @Transactional
    public void cancelAppointment(UUID id, String reason, UUID tenantId, UUID unitId) {
        ChangeAppointmentStatusRequest request = new ChangeAppointmentStatusRequest(
                AppointmentStatus.CANCELED, reason
        );
        changeStatus(id, request, tenantId, unitId);
    }

    private AppointmentResponse toAppointmentResponse(Appointment appointment, UUID tenantId) {
        Optional<CustomerSummaryResponse> customerOpt = crmContract.findCustomerSummary(appointment.getCustomerId(), tenantId);
        Optional<VehicleSummaryResponse> vehicleOpt = crmContract.findVehicleSummary(appointment.getVehicleId(), tenantId);

        String customerName = customerOpt.map(CustomerSummaryResponse::name).orElse("Cliente não identificado");
        String customerPhone = customerOpt.map(CustomerSummaryResponse::phone).orElse(null);

        String licensePlate = vehicleOpt.map(VehicleSummaryResponse::licensePlate).orElse("Sem placa");
        String formattedLicensePlate = vehicleOpt.map(VehicleSummaryResponse::formattedLicensePlate).orElse(licensePlate);
        String vehicleBrand = vehicleOpt.map(VehicleSummaryResponse::brand).orElse(null);
        String vehicleModel = vehicleOpt.map(VehicleSummaryResponse::model).orElse(null);

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getTenantId(),
                appointment.getUnitId(),
                appointment.getCustomerId(),
                customerName,
                customerPhone,
                appointment.getVehicleId(),
                licensePlate,
                formattedLicensePlate,
                vehicleBrand,
                vehicleModel,
                appointment.getScheduledAt(),
                appointment.getEstimatedEndAt(),
                appointment.getStatus(),
                appointment.getServiceType(),
                appointment.getNotes(),
                appointment.getCancellationReason(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }

    private AppointmentSummaryResponse toSummaryResponse(Appointment appointment, UUID tenantId) {
        Optional<CustomerSummaryResponse> customerOpt = crmContract.findCustomerSummary(appointment.getCustomerId(), tenantId);
        Optional<VehicleSummaryResponse> vehicleOpt = crmContract.findVehicleSummary(appointment.getVehicleId(), tenantId);

        String customerName = customerOpt.map(CustomerSummaryResponse::name).orElse("Cliente não identificado");
        String licensePlate = vehicleOpt.map(VehicleSummaryResponse::licensePlate).orElse("Sem placa");
        String formattedLicensePlate = vehicleOpt.map(VehicleSummaryResponse::formattedLicensePlate).orElse(licensePlate);
        String vehicleModel = vehicleOpt.map(VehicleSummaryResponse::model).orElse(null);

        return new AppointmentSummaryResponse(
                appointment.getId(),
                appointment.getUnitId(),
                appointment.getCustomerId(),
                customerName,
                appointment.getVehicleId(),
                licensePlate,
                formattedLicensePlate,
                vehicleModel,
                appointment.getScheduledAt(),
                appointment.getEstimatedEndAt(),
                appointment.getStatus(),
                appointment.getServiceType()
        );
    }

    private UUID resolveTenantId(UUID tenantId) {
        return tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    }
}
