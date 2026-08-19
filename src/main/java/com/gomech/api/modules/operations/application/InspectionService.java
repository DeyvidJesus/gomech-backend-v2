package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.CompleteInspectionRequest;
import com.gomech.api.modules.operations.api.dto.CreateInspectionRequest;
import com.gomech.api.modules.operations.api.dto.InspectionItemResponse;
import com.gomech.api.modules.operations.api.dto.InspectionResponse;
import com.gomech.api.modules.operations.api.dto.InspectionSummaryResponse;
import com.gomech.api.modules.operations.api.dto.SaveInspectionItemRequest;
import com.gomech.api.modules.operations.api.dto.UpdateInspectionRequest;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.InspectionAlreadyCompletedException;
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
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CrmContract crmContract;
    private final DomainEventBus domainEventBus;

    @Transactional
    public InspectionResponse createInspection(CreateInspectionRequest request, UUID tenantId, UUID unitId, UUID userId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);
        UUID effectiveUnitId = request.unitId() != null ? request.unitId() : unitId;

        // 1. Validação da relação Cliente x Veículo através do contrato do CRM
        boolean validAssociation = crmContract.validateCustomerAndVehicleAssociation(
                request.customerId(), request.vehicleId(), effectiveTenantId
        );
        if (!validAssociation) {
            throw new CustomerVehicleMismatchException(request.customerId(), request.vehicleId());
        }

        // 2. Validação do Agendamento (se informado)
        if (request.appointmentId() != null) {
            Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.appointmentId(), effectiveTenantId)
                    .orElseThrow(() -> new InvalidAppointmentInspectionLinkException(request.appointmentId(), "Agendamento não encontrado no tenant ativo"));

            if (!appointment.getVehicleId().equals(request.vehicleId())) {
                throw new InvalidAppointmentInspectionLinkException(request.appointmentId(), "O veículo do agendamento não coincide com o veículo da inspeção");
            }
            if (!appointment.getUnitId().equals(effectiveUnitId)) {
                throw new InvalidAppointmentInspectionLinkException(request.appointmentId(), "A unidade do agendamento não coincide com a unidade da inspeção");
            }
        }

        Inspection inspection = new Inspection();
        inspection.setTenantId(effectiveTenantId);
        inspection.setUnitId(effectiveUnitId);
        inspection.setCustomerId(request.customerId());
        inspection.setVehicleId(request.vehicleId());
        inspection.setAppointmentId(request.appointmentId());
        inspection.setInspectorUserId(userId);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setFuelLevel(request.fuelLevel());
        inspection.setCurrentMileage(request.currentMileage());
        inspection.setGeneralNotes(request.generalNotes() != null ? request.generalNotes().trim() : null);
        inspection.setStartedAt(OffsetDateTime.now());

        if (request.items() != null && !request.items().isEmpty()) {
            for (SaveInspectionItemRequest itemReq : request.items()) {
                InspectionItem item = toInspectionItemEntity(itemReq, effectiveTenantId);
                inspection.addItem(item);
            }
        }

        Inspection saved = inspectionRepository.save(inspection);
        log.info("Inspeção {} iniciada para o veículo {} e cliente {} na unidade {}",
                saved.getId(), saved.getVehicleId(), saved.getCustomerId(), effectiveUnitId);

        domainEventBus.publish(new InspectionCreatedEvent(
                effectiveTenantId,
                effectiveUnitId,
                saved.getId(),
                saved.getVehicleId(),
                saved.getCustomerId()
        ));

        return toInspectionResponse(saved, effectiveTenantId);
    }

    @Transactional
    public InspectionResponse updateInspection(UUID id, UpdateInspectionRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new InspectionNotFoundException(id));

        if (inspection.getStatus().isTerminal()) {
            throw new InspectionAlreadyCompletedException(id);
        }

        if (request.fuelLevel() != null) {
            inspection.setFuelLevel(request.fuelLevel());
        }
        if (request.currentMileage() != null) {
            inspection.setCurrentMileage(request.currentMileage());
        }
        if (request.generalNotes() != null) {
            inspection.setGeneralNotes(request.generalNotes().trim());
        }

        Inspection updated = inspectionRepository.save(inspection);
        log.info("Inspeção {} atualizada no tenant {}", id, effectiveTenantId);

        return toInspectionResponse(updated, effectiveTenantId);
    }

    @Transactional
    public InspectionResponse updateInspectionItems(UUID id, List<SaveInspectionItemRequest> itemRequests, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new InspectionNotFoundException(id));

        if (inspection.getStatus().isTerminal()) {
            throw new InspectionAlreadyCompletedException(id);
        }

        // Substituição completa dos itens
        inspection.getItems().clear();
        if (itemRequests != null) {
            for (SaveInspectionItemRequest itemReq : itemRequests) {
                InspectionItem item = toInspectionItemEntity(itemReq, effectiveTenantId);
                inspection.addItem(item);
            }
        }

        Inspection updated = inspectionRepository.save(inspection);
        log.info("Itens da inspeção {} atualizados (total: {}) no tenant {}", id, inspection.getItems().size(), effectiveTenantId);

        return toInspectionResponse(updated, effectiveTenantId);
    }

    @Transactional
    public InspectionResponse completeInspection(UUID id, CompleteInspectionRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new InspectionNotFoundException(id));

        if (inspection.getStatus().isTerminal()) {
            throw new InspectionAlreadyCompletedException(id);
        }

        if (request != null) {
            if (request.generalNotes() != null) {
                inspection.setGeneralNotes(request.generalNotes().trim());
            }
            if (request.finalItems() != null && !request.finalItems().isEmpty()) {
                inspection.getItems().clear();
                for (SaveInspectionItemRequest itemReq : request.finalItems()) {
                    InspectionItem item = toInspectionItemEntity(itemReq, effectiveTenantId);
                    inspection.addItem(item);
                }
            }
        }

        inspection.setStatus(InspectionStatus.COMPLETED);
        inspection.setCompletedAt(OffsetDateTime.now());

        Inspection completed = inspectionRepository.save(inspection);
        log.info("Inspeção {} finalizada com sucesso no tenant {}", id, effectiveTenantId);

        int total = completed.getItems().size();
        int critical = (int) completed.getItems().stream().filter(i -> i.getStatus() == InspectionItemStatus.CRITICAL).count();
        int attention = (int) completed.getItems().stream().filter(i -> i.getStatus() == InspectionItemStatus.ATTENTION).count();

        domainEventBus.publish(new InspectionCompletedEvent(
                effectiveTenantId,
                completed.getUnitId(),
                completed.getId(),
                completed.getVehicleId(),
                total,
                critical,
                attention
        ));

        return toInspectionResponse(completed, effectiveTenantId);
    }

    @Transactional(readOnly = true)
    public InspectionResponse getInspectionById(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new InspectionNotFoundException(id));

        return toInspectionResponse(inspection, effectiveTenantId);
    }

    @Transactional(readOnly = true)
    public Page<InspectionSummaryResponse> searchInspections(
            UUID vehicleId,
            UUID customerId,
            InspectionStatus status,
            UUID unitId,
            Pageable pageable,
            UUID tenantId
    ) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Specification<Inspection> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), effectiveTenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Inspection> page = inspectionRepository.findAll(spec, pageable);
        return page.map(ins -> toSummaryResponse(ins, effectiveTenantId));
    }

    @Transactional
    public void cancelInspection(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new InspectionNotFoundException(id));

        if (inspection.getStatus().isTerminal()) {
            throw new InspectionAlreadyCompletedException(id);
        }

        inspection.setStatus(InspectionStatus.CANCELED);
        inspection.setDeletedAt(OffsetDateTime.now());
        inspectionRepository.save(inspection);

        log.info("Inspeção {} cancelada no tenant {}", id, effectiveTenantId);
        domainEventBus.publish(new InspectionCanceledEvent(effectiveTenantId, inspection.getUnitId(), id));
    }

    private InspectionItem toInspectionItemEntity(SaveInspectionItemRequest req, UUID tenantId) {
        InspectionItem item = new InspectionItem();
        if (req.id() != null) {
            item.setId(req.id());
        }
        item.setTenantId(tenantId);
        item.setCategory(req.category());
        item.setName(req.name().trim());
        item.setStatus(req.status());
        item.setNotes(req.notes() != null ? req.notes().trim() : null);
        item.setRecommendedAction(req.recommendedAction() != null ? req.recommendedAction().trim() : null);
        item.setPhotoUrls(req.photoUrls() != null ? req.photoUrls().trim() : null);
        return item;
    }

    private InspectionResponse toInspectionResponse(Inspection inspection, UUID tenantId) {
        Optional<CustomerSummaryResponse> customerOpt = crmContract.findCustomerSummary(inspection.getCustomerId(), tenantId);
        Optional<VehicleSummaryResponse> vehicleOpt = crmContract.findVehicleSummary(inspection.getVehicleId(), tenantId);

        String customerName = customerOpt.map(CustomerSummaryResponse::name).orElse("Cliente não identificado");
        String customerPhone = customerOpt.map(CustomerSummaryResponse::phone).orElse(null);

        String licensePlate = vehicleOpt.map(VehicleSummaryResponse::licensePlate).orElse("Sem placa");
        String formattedLicensePlate = vehicleOpt.map(VehicleSummaryResponse::formattedLicensePlate).orElse(licensePlate);
        String vehicleBrand = vehicleOpt.map(VehicleSummaryResponse::brand).orElse(null);
        String vehicleModel = vehicleOpt.map(VehicleSummaryResponse::model).orElse(null);

        List<InspectionItemResponse> itemResponses = inspection.getItems().stream()
                .map(item -> new InspectionItemResponse(
                        item.getId(),
                        item.getCategory(),
                        item.getName(),
                        item.getStatus(),
                        item.getNotes(),
                        item.getRecommendedAction(),
                        item.getPhotoUrls(),
                        item.getCreatedAt(),
                        item.getUpdatedAt()
                ))
                .toList();

        int total = itemResponses.size();
        int ok = (int) itemResponses.stream().filter(i -> i.status() == InspectionItemStatus.OK).count();
        int attention = (int) itemResponses.stream().filter(i -> i.status() == InspectionItemStatus.ATTENTION).count();
        int critical = (int) itemResponses.stream().filter(i -> i.status() == InspectionItemStatus.CRITICAL).count();

        return new InspectionResponse(
                inspection.getId(),
                inspection.getTenantId(),
                inspection.getUnitId(),
                inspection.getCustomerId(),
                customerName,
                customerPhone,
                inspection.getVehicleId(),
                licensePlate,
                formattedLicensePlate,
                vehicleBrand,
                vehicleModel,
                inspection.getAppointmentId(),
                inspection.getInspectorUserId(),
                inspection.getStatus(),
                inspection.getFuelLevel(),
                inspection.getCurrentMileage(),
                inspection.getGeneralNotes(),
                total,
                ok,
                attention,
                critical,
                itemResponses,
                inspection.getStartedAt(),
                inspection.getCompletedAt(),
                inspection.getCreatedAt(),
                inspection.getUpdatedAt()
        );
    }

    private InspectionSummaryResponse toSummaryResponse(Inspection inspection, UUID tenantId) {
        Optional<CustomerSummaryResponse> customerOpt = crmContract.findCustomerSummary(inspection.getCustomerId(), tenantId);
        Optional<VehicleSummaryResponse> vehicleOpt = crmContract.findVehicleSummary(inspection.getVehicleId(), tenantId);

        String customerName = customerOpt.map(CustomerSummaryResponse::name).orElse("Cliente não identificado");
        String licensePlate = vehicleOpt.map(VehicleSummaryResponse::licensePlate).orElse("Sem placa");
        String formattedLicensePlate = vehicleOpt.map(VehicleSummaryResponse::formattedLicensePlate).orElse(licensePlate);
        String vehicleModel = vehicleOpt.map(VehicleSummaryResponse::model).orElse(null);

        int total = inspection.getItems().size();
        int critical = (int) inspection.getItems().stream().filter(i -> i.getStatus() == InspectionItemStatus.CRITICAL).count();
        int attention = (int) inspection.getItems().stream().filter(i -> i.getStatus() == InspectionItemStatus.ATTENTION).count();

        return new InspectionSummaryResponse(
                inspection.getId(),
                inspection.getUnitId(),
                inspection.getCustomerId(),
                customerName,
                inspection.getVehicleId(),
                licensePlate,
                formattedLicensePlate,
                vehicleModel,
                inspection.getAppointmentId(),
                inspection.getStatus(),
                total,
                critical,
                attention,
                inspection.getStartedAt(),
                inspection.getCompletedAt(),
                inspection.getCreatedAt()
        );
    }

    private UUID resolveTenantId(UUID tenantId) {
        return tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    }
}
