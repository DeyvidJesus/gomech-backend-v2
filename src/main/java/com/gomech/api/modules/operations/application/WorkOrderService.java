package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.events.*;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import com.gomech.api.modules.operations.infrastructure.persistence.model.QuoteItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrder;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrderItem;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.QuoteRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.WorkOrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    private final WorkOrderRepository workOrderRepository;
    private final QuoteRepository quoteRepository;
    private final CrmContract crmContract;
    private final DomainEventBus eventBus;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            QuoteRepository quoteRepository,
            CrmContract crmContract,
            DomainEventBus eventBus
    ) {
        this.workOrderRepository = workOrderRepository;
        this.quoteRepository = quoteRepository;
        this.crmContract = crmContract;
        this.eventBus = eventBus;
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request, UUID tenantId, UUID unitId, UUID userId) {
        log.info("Criando ordem de serviço para cliente {} e veículo {} no tenant {}", request.customerId(), request.vehicleId(), tenantId);

        crmContract.validateCustomerAndVehicleAssociation(request.customerId(), request.vehicleId(), tenantId);

        if (request.quoteId() != null) {
            Quote quote = quoteRepository.findByIdWithItems(request.quoteId(), tenantId)
                    .orElseThrow(() -> new QuoteNotFoundException(request.quoteId()));
            if (quote.getCustomerApprovalStatus() != CustomerApprovalStatus.APPROVED) {
                throw new QuoteNotEligibleForWorkOrderException(quote.getId(), quote.getStatus());
            }
            Optional<WorkOrder> existing = workOrderRepository.findByTenantIdAndQuoteIdAndDeletedAtIsNull(tenantId, quote.getId());
            if (existing.isPresent()) {
                throw new QuoteAlreadyConvertedException(quote.getId(), existing.get().getId());
            }
        }

        String orderNumber = generateOrderNumber();
        WorkOrderStatus initialStatus = request.mechanicUserId() != null ? WorkOrderStatus.OPEN : WorkOrderStatus.DRAFT;

        WorkOrder workOrder = new WorkOrder(
                tenantId,
                request.unitId() != null ? request.unitId() : unitId,
                orderNumber,
                request.customerId(),
                request.vehicleId(),
                request.quoteId(),
                request.mechanicUserId(),
                request.serviceBay(),
                initialStatus,
                request.startMileage(),
                request.diagnosisNotes(),
                request.technicalNotes(),
                request.customerNotes()
        );
        workOrder.setStartDate(request.startDate());
        workOrder.setEndDate(request.endDate());

        List<WorkOrderCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
        if (request.items() != null && !request.items().isEmpty()) {
            for (SaveWorkOrderItemRequest itemReq : request.items()) {
                WorkOrderCalculator.CalculatedItem calc = WorkOrderCalculator.calculateItem(
                        itemReq.quantity(),
                        itemReq.unitPrice(),
                        itemReq.discountAmount(),
                        itemReq.taxRate(),
                        itemReq.type()
                );
                calculatedItems.add(calc);

                WorkOrderItem item = new WorkOrderItem(
                        workOrder,
                        tenantId,
                        itemReq.type(),
                        itemReq.productId(),
                        itemReq.assignedMechanicId() != null ? itemReq.assignedMechanicId() : request.mechanicUserId(),
                        itemReq.name().trim(),
                        itemReq.description() != null ? itemReq.description().trim() : null,
                        itemReq.status() != null ? itemReq.status() : WorkOrderItemStatus.PENDING,
                        calc.quantity(),
                        calc.unitPrice(),
                        calc.discountAmount(),
                        calc.taxRate(),
                        calc.taxAmount(),
                        calc.totalAmount()
                );
                workOrder.addItem(item);
            }
        }

        WorkOrderCalculator.CalculatedTotals totals = WorkOrderCalculator.calculateTotals(calculatedItems);
        applyTotalsToWorkOrder(workOrder, totals);

        WorkOrder saved = workOrderRepository.save(workOrder);

        eventBus.publish(new WorkOrderCreatedEvent(
                saved.getId(),
                tenantId,
                saved.getUnitId(),
                saved.getOrderNumber(),
                saved.getCustomerId(),
                saved.getVehicleId(),
                saved.getQuoteId(),
                saved.getMechanicUserId(),
                saved.getTotalAmount()
        ));

        if (saved.getMechanicUserId() != null) {
            eventBus.publish(new WorkOrderAssignedEvent(
                    saved.getId(),
                    tenantId,
                    saved.getUnitId(),
                    saved.getMechanicUserId(),
                    saved.getServiceBay()
            ));
        }

        return toResponse(saved, tenantId);
    }

    @Transactional
    public WorkOrderResponse createFromQuote(UUID quoteId, UUID tenantId, UUID unitId, UUID userId) {
        log.info("Convertendo orçamento {} em ordem de serviço no tenant {}", quoteId, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(quoteId, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));

        if (quote.getCustomerApprovalStatus() != CustomerApprovalStatus.APPROVED) {
            throw new QuoteNotEligibleForWorkOrderException(quote.getId(), quote.getStatus());
        }

        Optional<WorkOrder> existing = workOrderRepository.findByTenantIdAndQuoteIdAndDeletedAtIsNull(tenantId, quoteId);
        if (existing.isPresent()) {
            throw new QuoteAlreadyConvertedException(quoteId, existing.get().getId());
        }

        String orderNumber = generateOrderNumber();
        WorkOrder workOrder = new WorkOrder(
                tenantId,
                quote.getUnitId(),
                orderNumber,
                quote.getCustomerId(),
                quote.getVehicleId(),
                quote.getId(),
                null,
                null,
                WorkOrderStatus.OPEN,
                null,
                "Convertido do Orçamento aprovado " + quote.getId(),
                quote.getNotes(),
                quote.getTermsAndConditions()
        );

        List<WorkOrderCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
        if (quote.getItems() != null) {
            for (QuoteItem quoteItem : quote.getItems()) {
                WorkOrderItemType type = quoteItem.getType() == QuoteItemType.LABOR
                        ? WorkOrderItemType.SERVICE
                        : WorkOrderItemType.PART;

                WorkOrderCalculator.CalculatedItem calc = WorkOrderCalculator.calculateItem(
                        quoteItem.getQuantity(),
                        quoteItem.getUnitPrice(),
                        quoteItem.getDiscountAmount(),
                        quoteItem.getTaxRate(),
                        type
                );
                calculatedItems.add(calc);

                WorkOrderItem item = new WorkOrderItem(
                        workOrder,
                        tenantId,
                        type,
                        quoteItem.getProductId(),
                        null,
                        quoteItem.getName(),
                        quoteItem.getDescription(),
                        WorkOrderItemStatus.PENDING,
                        calc.quantity(),
                        calc.unitPrice(),
                        calc.discountAmount(),
                        calc.taxRate(),
                        calc.taxAmount(),
                        calc.totalAmount()
                );
                workOrder.addItem(item);
            }
        }

        WorkOrderCalculator.CalculatedTotals totals = WorkOrderCalculator.calculateTotals(calculatedItems);
        applyTotalsToWorkOrder(workOrder, totals);

        WorkOrder saved = workOrderRepository.save(workOrder);

        eventBus.publish(new WorkOrderCreatedEvent(
                saved.getId(),
                tenantId,
                saved.getUnitId(),
                saved.getOrderNumber(),
                saved.getCustomerId(),
                saved.getVehicleId(),
                saved.getQuoteId(),
                saved.getMechanicUserId(),
                saved.getTotalAmount()
        ));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public WorkOrderResponse updateWorkOrder(UUID id, UpdateWorkOrderRequest request, UUID tenantId) {
        log.info("Atualizando ordem de serviço {} no tenant {}", id, tenantId);

        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        if (WorkOrderLifecycleValidator.isTerminal(workOrder.getStatus())) {
            throw new WorkOrderAlreadyCompletedException(id);
        }

        boolean mechanicAssigned = request.mechanicUserId() != null && !Objects.equals(workOrder.getMechanicUserId(), request.mechanicUserId());

        if (request.mechanicUserId() != null) {
            workOrder.setMechanicUserId(request.mechanicUserId());
        }
        if (request.serviceBay() != null) {
            workOrder.setServiceBay(request.serviceBay());
        }
        if (request.startMileage() != null) {
            workOrder.setStartMileage(request.startMileage());
        }
        if (request.startDate() != null) {
            workOrder.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            workOrder.setEndDate(request.endDate());
        }
        if (request.technicalNotes() != null) {
            workOrder.setTechnicalNotes(request.technicalNotes());
        }
        if (request.diagnosisNotes() != null) {
            workOrder.setDiagnosisNotes(request.diagnosisNotes());
        }
        if (request.customerNotes() != null) {
            workOrder.setCustomerNotes(request.customerNotes());
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        if (mechanicAssigned) {
            eventBus.publish(new WorkOrderAssignedEvent(
                    saved.getId(),
                    tenantId,
                    saved.getUnitId(),
                    saved.getMechanicUserId(),
                    saved.getServiceBay()
            ));
        }

        return toResponse(saved, tenantId);
    }

    @Transactional
    public WorkOrderResponse updateWorkOrderItems(UUID id, List<SaveWorkOrderItemRequest> itemRequests, UUID tenantId) {
        log.info("Atualizando itens da ordem de serviço {} no tenant {}", id, tenantId);

        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        if (!WorkOrderLifecycleValidator.canModifyItems(workOrder.getStatus())) {
            throw new WorkOrderAlreadyCompletedException(id);
        }

        workOrder.clearItems();

        List<WorkOrderCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
        if (itemRequests != null && !itemRequests.isEmpty()) {
            for (SaveWorkOrderItemRequest req : itemRequests) {
                WorkOrderCalculator.CalculatedItem calc = WorkOrderCalculator.calculateItem(
                        req.quantity(),
                        req.unitPrice(),
                        req.discountAmount(),
                        req.taxRate(),
                        req.type()
                );
                calculatedItems.add(calc);

                WorkOrderItem item = new WorkOrderItem(
                        workOrder,
                        tenantId,
                        req.type(),
                        req.productId(),
                        req.assignedMechanicId() != null ? req.assignedMechanicId() : workOrder.getMechanicUserId(),
                        req.name().trim(),
                        req.description() != null ? req.description().trim() : null,
                        req.status() != null ? req.status() : WorkOrderItemStatus.PENDING,
                        calc.quantity(),
                        calc.unitPrice(),
                        calc.discountAmount(),
                        calc.taxRate(),
                        calc.taxAmount(),
                        calc.totalAmount()
                );
                workOrder.addItem(item);
            }
        }

        WorkOrderCalculator.CalculatedTotals totals = WorkOrderCalculator.calculateTotals(calculatedItems);
        applyTotalsToWorkOrder(workOrder, totals);

        WorkOrder saved = workOrderRepository.save(workOrder);
        return toResponse(saved, tenantId);
    }

    @Transactional
    public WorkOrderResponse changeStatus(UUID id, ChangeWorkOrderStatusRequest request, UUID tenantId) {
        log.info("Alterando status da ordem de serviço {} para {} no tenant {}", id, request.status(), tenantId);

        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        WorkOrderStatus previousStatus = workOrder.getStatus();
        WorkOrderLifecycleValidator.validateTransition(previousStatus, request.status());

        if (request.status() == WorkOrderStatus.COMPLETED) {
            return completeWorkOrder(id, new CompleteWorkOrderRequest(null, request.notes(), null), tenantId);
        }

        if (request.status() == WorkOrderStatus.CANCELED) {
            workOrder.setCanceledAt(OffsetDateTime.now());
            workOrder.setCancellationReason(request.notes());
            workOrder.setStatus(WorkOrderStatus.CANCELED);
            workOrderRepository.save(workOrder);
            eventBus.publish(new WorkOrderCanceledEvent(id, tenantId, workOrder.getUnitId(), request.notes()));
            return toResponse(workOrder, tenantId);
        }

        if (request.status() == WorkOrderStatus.IN_PROGRESS && workOrder.getStartDate() == null) {
            workOrder.setStartDate(OffsetDateTime.now());
        }

        workOrder.setStatus(request.status());
        if (request.notes() != null && !request.notes().isBlank()) {
            String updatedNotes = workOrder.getTechnicalNotes() != null
                    ? workOrder.getTechnicalNotes() + "\n" + request.notes()
                    : request.notes();
            workOrder.setTechnicalNotes(updatedNotes);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        eventBus.publish(new WorkOrderStatusChangedEvent(
                id,
                tenantId,
                saved.getUnitId(),
                previousStatus,
                request.status()
        ));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public WorkOrderResponse completeWorkOrder(UUID id, CompleteWorkOrderRequest request, UUID tenantId) {
        log.info("Finalizando ordem de serviço {} no tenant {}", id, tenantId);

        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        WorkOrderStatus previousStatus = workOrder.getStatus();
        WorkOrderLifecycleValidator.validateTransition(previousStatus, WorkOrderStatus.COMPLETED);

        OffsetDateTime now = OffsetDateTime.now();
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        workOrder.setCompletedAt(now);
        if (workOrder.getEndDate() == null) {
            workOrder.setEndDate(now);
        }
        if (request != null && request.endMileage() != null) {
            workOrder.setEndMileage(request.endMileage());
        }
        if (request != null && request.technicalNotes() != null && !request.technicalNotes().isBlank()) {
            String notes = workOrder.getTechnicalNotes() != null
                    ? workOrder.getTechnicalNotes() + "\n" + request.technicalNotes()
                    : request.technicalNotes();
            workOrder.setTechnicalNotes(notes);
        }
        if (request != null && request.customerNotes() != null && !request.customerNotes().isBlank()) {
            workOrder.setCustomerNotes(request.customerNotes());
        }

        // Marcar todos os itens pendentes como concluídos
        if (workOrder.getItems() != null) {
            for (WorkOrderItem item : workOrder.getItems()) {
                if (item.getStatus() == WorkOrderItemStatus.PENDING || item.getStatus() == WorkOrderItemStatus.IN_PROGRESS) {
                    item.setStatus(WorkOrderItemStatus.COMPLETED);
                }
            }
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        int itemCount = saved.getItems() != null ? saved.getItems().size() : 0;

        // Publicar evento transacional de conclusão para consumo desacoplado (Estoque/Financeiro)
        eventBus.publish(new WorkOrderCompletedEvent(
                saved.getId(),
                tenantId,
                saved.getUnitId(),
                saved.getOrderNumber(),
                saved.getCustomerId(),
                saved.getVehicleId(),
                saved.getQuoteId(),
                saved.getMechanicUserId(),
                saved.getTotalAmount(),
                saved.getTotalPartsAmount(),
                saved.getTotalServicesAmount(),
                saved.getCompletedAt(),
                saved.getEndMileage(),
                itemCount
        ));

        eventBus.publish(new WorkOrderStatusChangedEvent(
                saved.getId(),
                tenantId,
                saved.getUnitId(),
                previousStatus,
                WorkOrderStatus.COMPLETED
        ));

        return toResponse(saved, tenantId);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderById(UUID id, UUID tenantId) {
        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
        return toResponse(workOrder, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderSummaryResponse> searchWorkOrders(
            UUID customerId,
            UUID vehicleId,
            UUID mechanicId,
            WorkOrderStatus status,
            UUID unitId,
            Pageable pageable,
            UUID tenantId
    ) {
        Specification<WorkOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (mechanicId != null) {
                predicates.add(cb.equal(root.get("mechanicUserId"), mechanicId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WorkOrder> page = workOrderRepository.findAll(spec, pageable);
        return page.map(wo -> toSummaryResponse(wo, tenantId));
    }

    @Transactional(readOnly = true)
    public WorkOrderKanbanResponse getKanbanBoard(UUID unitId, UUID tenantId) {
        List<WorkOrderStatus> activeStatuses = List.of(
                WorkOrderStatus.OPEN,
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.WAITING_PARTS,
                WorkOrderStatus.WAITING_CUSTOMER
        );

        List<WorkOrder> activeOrders = workOrderRepository.findByTenantIdAndUnitIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId,
                unitId,
                activeStatuses
        );

        Map<WorkOrderStatus, List<WorkOrderSummaryResponse>> grouped = new EnumMap<>(WorkOrderStatus.class);
        for (WorkOrderStatus st : activeStatuses) {
            grouped.put(st, new ArrayList<>());
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (WorkOrder wo : activeOrders) {
            WorkOrderSummaryResponse summary = toSummaryResponse(wo, tenantId);
            grouped.get(wo.getStatus()).add(summary);
            grandTotal = grandTotal.add(wo.getTotalAmount());
        }

        List<KanbanColumnResponse> columns = List.of(
                buildColumn(WorkOrderStatus.OPEN, "Abertas", grouped.get(WorkOrderStatus.OPEN)),
                buildColumn(WorkOrderStatus.IN_PROGRESS, "Em Execução", grouped.get(WorkOrderStatus.IN_PROGRESS)),
                buildColumn(WorkOrderStatus.WAITING_PARTS, "Aguardando Peças", grouped.get(WorkOrderStatus.WAITING_PARTS)),
                buildColumn(WorkOrderStatus.WAITING_CUSTOMER, "Aguardando Cliente", grouped.get(WorkOrderStatus.WAITING_CUSTOMER))
        );

        return new WorkOrderKanbanResponse(
                unitId,
                activeOrders.size(),
                grandTotal,
                columns
        );
    }

    @Transactional
    public void cancelWorkOrder(UUID id, String reason, UUID tenantId) {
        log.info("Cancelando ordem de serviço {} no tenant {}", id, tenantId);

        WorkOrder workOrder = workOrderRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        WorkOrderLifecycleValidator.validateTransition(workOrder.getStatus(), WorkOrderStatus.CANCELED);

        workOrder.setStatus(WorkOrderStatus.CANCELED);
        workOrder.setCanceledAt(OffsetDateTime.now());
        workOrder.setCancellationReason(reason);

        workOrderRepository.save(workOrder);
        workOrderRepository.delete(workOrder); // soft delete via @SQLDelete

        eventBus.publish(new WorkOrderCanceledEvent(id, tenantId, workOrder.getUnitId(), reason));
    }

    private KanbanColumnResponse buildColumn(WorkOrderStatus status, String title, List<WorkOrderSummaryResponse> orders) {
        BigDecimal total = orders.stream()
                .map(WorkOrderSummaryResponse::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new KanbanColumnResponse(status, title, orders.size(), total, orders);
    }

    private void applyTotalsToWorkOrder(WorkOrder workOrder, WorkOrderCalculator.CalculatedTotals totals) {
        workOrder.setSubtotalAmount(totals.subtotalAmount());
        workOrder.setDiscountAmount(totals.discountAmount());
        workOrder.setTaxAmount(totals.taxAmount());
        workOrder.setTotalServicesAmount(totals.totalServicesAmount());
        workOrder.setTotalPartsAmount(totals.totalPartsAmount());
        workOrder.setTotalAmount(totals.totalAmount());
    }

    private WorkOrderSummaryResponse toSummaryResponse(WorkOrder wo, UUID tenantId) {
        CustomerSummaryResponse customer = crmContract.findCustomerSummary(wo.getCustomerId(), tenantId).orElse(null);
        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(wo.getVehicleId(), tenantId).orElse(null);

        int count = wo.getItems() != null ? wo.getItems().size() : 0;
        int completedCount = wo.getItems() != null
                ? (int) wo.getItems().stream().filter(i -> i.getStatus() == WorkOrderItemStatus.COMPLETED).count()
                : 0;

        return new WorkOrderSummaryResponse(
                wo.getId(),
                wo.getUnitId(),
                wo.getOrderNumber(),
                wo.getCustomerId(),
                customer != null ? customer.name() : null,
                wo.getVehicleId(),
                vehicle != null ? vehicle.licensePlate() : null,
                vehicle != null ? vehicle.formattedLicensePlate() : null,
                vehicle != null ? vehicle.brand() : null,
                vehicle != null ? vehicle.model() : null,
                wo.getQuoteId(),
                wo.getMechanicUserId(),
                null, // mechanic name can be resolved if needed
                wo.getServiceBay(),
                wo.getStatus(),
                wo.getTotalServicesAmount(),
                wo.getTotalPartsAmount(),
                wo.getTotalAmount(),
                count,
                completedCount,
                wo.getStartDate(),
                wo.getEndDate(),
                wo.getCompletedAt(),
                wo.getCreatedAt()
        );
    }

    private WorkOrderResponse toResponse(WorkOrder wo, UUID tenantId) {
        CustomerSummaryResponse customer = crmContract.findCustomerSummary(wo.getCustomerId(), tenantId).orElse(null);
        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(wo.getVehicleId(), tenantId).orElse(null);

        List<WorkOrderItemResponse> itemResponses = wo.getItems() != null
                ? wo.getItems().stream().map(this::toItemResponse).toList()
                : List.of();

        return new WorkOrderResponse(
                wo.getId(),
                wo.getUnitId(),
                wo.getOrderNumber(),
                wo.getCustomerId(),
                customer != null ? customer.name() : null,
                customer != null ? customer.formattedDocument() : null,
                customer != null ? customer.phone() : null,
                wo.getVehicleId(),
                vehicle != null ? vehicle.licensePlate() : null,
                vehicle != null ? vehicle.formattedLicensePlate() : null,
                vehicle != null ? vehicle.brand() : null,
                vehicle != null ? vehicle.model() : null,
                vehicle != null ? vehicle.year() : null,
                wo.getQuoteId(),
                wo.getMechanicUserId(),
                null, // mechanic name
                wo.getServiceBay(),
                wo.getStatus(),
                wo.getStartMileage(),
                wo.getEndMileage(),
                wo.getSubtotalAmount(),
                wo.getDiscountAmount(),
                wo.getTaxAmount(),
                wo.getTotalServicesAmount(),
                wo.getTotalPartsAmount(),
                wo.getTotalAmount(),
                wo.getStartDate(),
                wo.getEndDate(),
                wo.getCompletedAt(),
                wo.getCanceledAt(),
                wo.getCancellationReason(),
                wo.getTechnicalNotes(),
                wo.getDiagnosisNotes(),
                wo.getCustomerNotes(),
                itemResponses,
                wo.getCreatedAt(),
                wo.getUpdatedAt(),
                wo.getVersion()
        );
    }

    private WorkOrderItemResponse toItemResponse(WorkOrderItem item) {
        return new WorkOrderItemResponse(
                item.getId(),
                item.getWorkOrder() != null ? item.getWorkOrder().getId() : null,
                item.getType(),
                item.getProductId(),
                item.getAssignedMechanicId(),
                null,
                item.getName(),
                item.getDescription(),
                item.getStatus(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getTaxRate(),
                item.getTaxAmount(),
                item.getTotalAmount(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private String generateOrderNumber() {
        return "OS-" + (System.currentTimeMillis() % 10000000);
    }
}
