package com.gomech.api.modules.operations.application;

import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.VehicleHistoryContract;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import com.gomech.api.modules.operations.infrastructure.persistence.model.InspectionItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrder;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrderItem;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.InspectionRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class VehicleHistoryService implements VehicleHistoryContract {

    private static final Logger log = LoggerFactory.getLogger(VehicleHistoryService.class);

    private final WorkOrderRepository workOrderRepository;
    private final InspectionRepository inspectionRepository;
    private final CrmContract crmContract;

    public VehicleHistoryService(
            WorkOrderRepository workOrderRepository,
            InspectionRepository inspectionRepository,
            CrmContract crmContract
    ) {
        this.workOrderRepository = workOrderRepository;
        this.inspectionRepository = inspectionRepository;
        this.crmContract = crmContract;
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleServiceHistoryResponse getVehicleServiceHistory(UUID vehicleId, UUID tenantId) {
        log.info("Consultando histórico de serviços do veículo {} no tenant {}", vehicleId, tenantId);

        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(vehicleId, tenantId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        CustomerSummaryResponse customer = vehicle.customerId() != null
                ? crmContract.findCustomerSummary(vehicle.customerId(), tenantId).orElse(null)
                : null;

        List<WorkOrder> completedWorkOrders = workOrderRepository.findCompletedByVehicleWithItems(
                tenantId, vehicleId, WorkOrderStatus.COMPLETED
        );

        List<Inspection> inspections = inspectionRepository.findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId, vehicleId
        ).stream().filter(i -> i.getStatus() == InspectionStatus.COMPLETED).toList();

        VehicleServiceHistoryMetricsResponse metrics = calculateMetrics(completedWorkOrders, inspections, vehicle);
        List<VehicleHistoricalWorkOrderResponse> workOrderResponses = mapWorkOrders(completedWorkOrders);
        List<VehicleHistoricalInspectionResponse> inspectionResponses = mapInspections(inspections);

        return new VehicleServiceHistoryResponse(
                vehicle.id(),
                vehicle.licensePlate(),
                vehicle.formattedLicensePlate(),
                vehicle.brand(),
                vehicle.model(),
                vehicle.year(),
                vehicle.currentMileage(),
                customer,
                metrics,
                workOrderResponses,
                inspectionResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleServiceHistoryExportResponse getVehicleServiceHistoryExport(UUID vehicleId, UUID tenantId) {
        log.info("Gerando dossiê exportável de histórico do veículo {} no tenant {}", vehicleId, tenantId);

        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(vehicleId, tenantId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        CustomerSummaryResponse customer = vehicle.customerId() != null
                ? crmContract.findCustomerSummary(vehicle.customerId(), tenantId).orElse(null)
                : null;

        List<WorkOrder> completedWorkOrders = workOrderRepository.findCompletedByVehicleWithItems(
                tenantId, vehicleId, WorkOrderStatus.COMPLETED
        );

        List<Inspection> inspections = inspectionRepository.findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId, vehicleId
        ).stream().filter(i -> i.getStatus() == InspectionStatus.COMPLETED).toList();

        VehicleServiceHistoryMetricsResponse metrics = calculateMetrics(completedWorkOrders, inspections, vehicle);
        List<VehicleHistoricalWorkOrderResponse> workOrderResponses = mapWorkOrders(completedWorkOrders);

        String reportId = "DOSSIER-" + vehicle.licensePlate() + "-" + System.currentTimeMillis();
        String verificationCode = "GM-AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String warrantyNotice = "Certificado de Manutenção GoMech. Todos os serviços executados possuem garantia técnica de 90 dias conforme o Art. 26 do Código de Defesa do Consumidor.";

        return new VehicleServiceHistoryExportResponse(
                reportId,
                OffsetDateTime.now(),
                "Oficina Autorizada GoMech",
                vehicle,
                customer,
                metrics,
                workOrderResponses,
                verificationCode,
                warrantyNotice
        );
    }

    private VehicleServiceHistoryMetricsResponse calculateMetrics(
            List<WorkOrder> workOrders,
            List<Inspection> inspections,
            VehicleSummaryResponse vehicle
    ) {
        int totalServicesCount = workOrders.size();

        BigDecimal totalSpent = workOrders.stream()
                .map(WorkOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal averageTicket = totalServicesCount > 0
                ? totalSpent.divide(BigDecimal.valueOf(totalServicesCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        OffsetDateTime firstServiceDate = null;
        OffsetDateTime lastServiceDate = null;

        if (!workOrders.isEmpty()) {
            lastServiceDate = workOrders.get(0).getCompletedAt() != null
                    ? workOrders.get(0).getCompletedAt()
                    : workOrders.get(0).getCreatedAt();

            WorkOrder oldest = workOrders.get(workOrders.size() - 1);
            firstServiceDate = oldest.getCompletedAt() != null
                    ? oldest.getCompletedAt()
                    : oldest.getCreatedAt();
        }

        int maxMileage = vehicle.currentMileage() != null ? vehicle.currentMileage() : 0;
        for (WorkOrder wo : workOrders) {
            if (wo.getEndMileage() != null && wo.getEndMileage() > maxMileage) {
                maxMileage = wo.getEndMileage();
            }
            if (wo.getStartMileage() != null && wo.getStartMileage() > maxMileage) {
                maxMileage = wo.getStartMileage();
            }
        }
        for (Inspection insp : inspections) {
            if (insp.getCurrentMileage() != null && insp.getCurrentMileage() > maxMileage) {
                maxMileage = insp.getCurrentMileage();
            }
        }

        int totalPartsCount = 0;
        for (WorkOrder wo : workOrders) {
            if (wo.getItems() != null) {
                for (WorkOrderItem item : wo.getItems()) {
                    if (item.getType() == WorkOrderItemType.PART) {
                        totalPartsCount += item.getQuantity() != null ? item.getQuantity().intValue() : 1;
                    }
                }
            }
        }

        return new VehicleServiceHistoryMetricsResponse(
                totalServicesCount,
                totalSpent,
                averageTicket,
                firstServiceDate,
                lastServiceDate,
                maxMileage,
                totalPartsCount
        );
    }

    private List<VehicleHistoricalWorkOrderResponse> mapWorkOrders(List<WorkOrder> workOrders) {
        return workOrders.stream().map(wo -> {
            List<VehicleHistoricalItemResponse> items = wo.getItems() != null
                    ? wo.getItems().stream().map(i -> new VehicleHistoricalItemResponse(
                    i.getId(),
                    i.getType(),
                    i.getName(),
                    i.getDescription(),
                    i.getQuantity(),
                    i.getUnitPrice(),
                    i.getTotalAmount()
            )).toList()
                    : List.of();

            return new VehicleHistoricalWorkOrderResponse(
                    wo.getId(),
                    wo.getOrderNumber(),
                    wo.getServiceBay(),
                    wo.getCompletedAt() != null ? wo.getCompletedAt() : wo.getCreatedAt(),
                    wo.getEndMileage() != null ? wo.getEndMileage() : wo.getStartMileage(),
                    wo.getTotalAmount(),
                    wo.getTotalPartsAmount(),
                    wo.getTotalServicesAmount(),
                    wo.getTechnicalNotes(),
                    wo.getCustomerNotes(),
                    items
            );
        }).toList();
    }

    private List<VehicleHistoricalInspectionResponse> mapInspections(List<Inspection> inspections) {
        return inspections.stream().map(insp -> {
            List<InspectionItem> items = insp.getItems() != null ? insp.getItems() : List.of();
            int okCount = (int) items.stream().filter(i -> i.getStatus() == InspectionItemStatus.OK).count();
            int attentionCount = (int) items.stream().filter(i -> i.getStatus() == InspectionItemStatus.ATTENTION).count();
            int criticalCount = (int) items.stream().filter(i -> i.getStatus() == InspectionItemStatus.CRITICAL).count();

            List<String> criticalIssues = items.stream()
                    .filter(i -> i.getStatus() == InspectionItemStatus.CRITICAL)
                    .map(i -> i.getName() + (i.getNotes() != null ? ": " + i.getNotes() : ""))
                    .toList();

            return new VehicleHistoricalInspectionResponse(
                    insp.getId(),
                    insp.getCompletedAt() != null ? insp.getCompletedAt() : insp.getCreatedAt(),
                    insp.getCurrentMileage(),
                    insp.getFuelLevel() != null ? insp.getFuelLevel().name() : null,
                    insp.getGeneralNotes(),
                    items.size(),
                    okCount,
                    attentionCount,
                    criticalCount,
                    criticalIssues
            );
        }).toList();
    }
}
