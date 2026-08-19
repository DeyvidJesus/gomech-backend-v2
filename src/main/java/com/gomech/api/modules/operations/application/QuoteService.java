package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
import com.gomech.api.modules.operations.events.*;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import com.gomech.api.modules.operations.infrastructure.persistence.model.InspectionItem;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import com.gomech.api.modules.operations.infrastructure.persistence.model.QuoteItem;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.AppointmentRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.InspectionRepository;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.QuoteRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final QuoteRepository quoteRepository;
    private final InspectionRepository inspectionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CrmContract crmContract;
    private final DomainEventBus eventBus;

    public QuoteService(QuoteRepository quoteRepository,
                        InspectionRepository inspectionRepository,
                        AppointmentRepository appointmentRepository,
                        CrmContract crmContract,
                        DomainEventBus eventBus) {
        this.quoteRepository = quoteRepository;
        this.inspectionRepository = inspectionRepository;
        this.appointmentRepository = appointmentRepository;
        this.crmContract = crmContract;
        this.eventBus = eventBus;
    }

    @Transactional
    public QuoteResponse createQuote(CreateQuoteRequest request, UUID tenantId, UUID unitId, UUID userId) {
        log.info("Criando novo orçamento para cliente {} e veículo {} no tenant {}", request.customerId(), request.vehicleId(), tenantId);

        crmContract.validateCustomerAndVehicleAssociation(request.customerId(), request.vehicleId(), tenantId);

        if (request.inspectionId() != null) {
            Inspection inspection = inspectionRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.inspectionId(), tenantId)
                    .orElseThrow(() -> new InspectionNotFoundException(request.inspectionId()));
            if (!inspection.getCustomerId().equals(request.customerId()) || !inspection.getVehicleId().equals(request.vehicleId())) {
                throw new InvalidAppointmentInspectionLinkException("A vistoria informada não pertence ao mesmo cliente e veículo do orçamento.");
            }
        }

        if (request.appointmentId() != null) {
            appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.appointmentId(), tenantId)
                    .orElseThrow(() -> new AppointmentNotFoundException(request.appointmentId()));
        }

        UUID resolvedUnitId = request.unitId() != null ? request.unitId() : unitId;
        Quote quote = new Quote(
                tenantId,
                resolvedUnitId,
                request.customerId(),
                request.vehicleId(),
                request.inspectionId(),
                request.appointmentId(),
                userId,
                request.validUntil(),
                request.notes(),
                request.termsAndConditions()
        );

        if (request.items() != null && !request.items().isEmpty()) {
            List<QuoteCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
            for (SaveQuoteItemRequest itemReq : request.items()) {
                QuoteCalculator.CalculatedItem calc = QuoteCalculator.calculateItem(
                        itemReq.quantity(),
                        itemReq.unitPrice(),
                        itemReq.discountAmount(),
                        itemReq.taxRate(),
                        itemReq.type()
                );
                calculatedItems.add(calc);

                QuoteItem item = new QuoteItem(
                        quote,
                        tenantId,
                        calc.type(),
                        itemReq.productId(),
                        itemReq.name(),
                        itemReq.description(),
                        calc.quantity(),
                        calc.unitPrice(),
                        calc.discountAmount(),
                        calc.taxRate(),
                        calc.taxAmount(),
                        calc.totalAmount()
                );
                quote.addItem(item);
            }

            QuoteCalculator.CalculatedTotals totals = QuoteCalculator.calculateTotals(calculatedItems);
            applyTotalsToQuote(quote, totals);
        }

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteCreatedEvent(saved.getId(), tenantId, saved.getUnitId(), saved.getCustomerId(), saved.getVehicleId(), saved.getInspectionId(), saved.getTotalAmount()));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse createQuoteFromInspection(UUID inspectionId, UUID tenantId, UUID unitId, UUID userId) {
        log.info("Gerando orçamento a partir da vistoria {} no tenant {}", inspectionId, tenantId);

        Inspection inspection = inspectionRepository.findByIdWithItems(inspectionId, tenantId)
                .orElseThrow(() -> new InspectionNotFoundException(inspectionId));

        Quote quote = new Quote(
                tenantId,
                inspection.getUnitId(),
                inspection.getCustomerId(),
                inspection.getVehicleId(),
                inspection.getId(),
                inspection.getAppointmentId(),
                userId,
                OffsetDateTime.now().plusDays(15),
                "Orçamento gerado a partir do checklist de vistoria veicular.",
                "Validade da proposta: 15 dias. Peças e serviços sujeitos a confirmação de disponibilidade."
        );

        List<QuoteCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
        if (inspection.getItems() != null) {
            for (InspectionItem inspItem : inspection.getItems()) {
                if (inspItem.getStatus() == InspectionItemStatus.ATTENTION || inspItem.getStatus() == InspectionItemStatus.CRITICAL) {
                    String itemName = inspItem.getName() + " (" + inspItem.getCategory().name() + ")";
                    String description = inspItem.getRecommendedAction() != null
                            ? inspItem.getRecommendedAction()
                            : (inspItem.getNotes() != null ? inspItem.getNotes() : "Correção recomendada em vistoria técnica.");

                    QuoteCalculator.CalculatedItem calc = QuoteCalculator.calculateItem(
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            QuoteItemType.PART
                    );
                    calculatedItems.add(calc);

                    QuoteItem item = new QuoteItem(
                            quote,
                            tenantId,
                            QuoteItemType.PART,
                            null,
                            itemName,
                            description,
                            calc.quantity(),
                            calc.unitPrice(),
                            calc.discountAmount(),
                            calc.taxRate(),
                            calc.taxAmount(),
                            calc.totalAmount()
                    );
                    quote.addItem(item);
                }
            }
        }

        QuoteCalculator.CalculatedTotals totals = QuoteCalculator.calculateTotals(calculatedItems);
        applyTotalsToQuote(quote, totals);

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteCreatedEvent(saved.getId(), tenantId, saved.getUnitId(), saved.getCustomerId(), saved.getVehicleId(), saved.getInspectionId(), saved.getTotalAmount()));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse updateQuote(UUID id, UpdateQuoteRequest request, UUID tenantId) {
        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateCanModify(quote.getStatus());

        if (request.validUntil() != null) {
            quote.setValidUntil(request.validUntil());
        }
        if (request.notes() != null) {
            quote.setNotes(request.notes());
        }
        if (request.termsAndConditions() != null) {
            quote.setTermsAndConditions(request.termsAndConditions());
        }

        Quote saved = quoteRepository.save(quote);
        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse updateQuoteItems(UUID id, List<SaveQuoteItemRequest> items, UUID tenantId) {
        log.info("Atualizando itens e recalculando totais do orçamento {} no tenant {}", id, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateCanModify(quote.getStatus());

        quote.clearItems();

        List<QuoteCalculator.CalculatedItem> calculatedItems = new ArrayList<>();
        if (items != null) {
            for (SaveQuoteItemRequest itemReq : items) {
                QuoteCalculator.CalculatedItem calc = QuoteCalculator.calculateItem(
                        itemReq.quantity(),
                        itemReq.unitPrice(),
                        itemReq.discountAmount(),
                        itemReq.taxRate(),
                        itemReq.type()
                );
                calculatedItems.add(calc);

                QuoteItem item = new QuoteItem(
                        quote,
                        tenantId,
                        calc.type(),
                        itemReq.productId(),
                        itemReq.name(),
                        itemReq.description(),
                        calc.quantity(),
                        calc.unitPrice(),
                        calc.discountAmount(),
                        calc.taxRate(),
                        calc.taxAmount(),
                        calc.totalAmount()
                );
                quote.addItem(item);
            }
        }

        QuoteCalculator.CalculatedTotals totals = QuoteCalculator.calculateTotals(calculatedItems);
        applyTotalsToQuote(quote, totals);

        Quote saved = quoteRepository.save(quote);
        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse submitForInternalApproval(UUID id, UUID tenantId) {
        log.info("Submetendo orçamento {} para aprovação interna no tenant {}", id, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.PENDING_INTERNAL_APPROVAL);
        quote.setStatus(QuoteStatus.PENDING_INTERNAL_APPROVAL);

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteSubmittedForApprovalEvent(saved.getId(), tenantId, saved.getUnitId(), saved.getTotalAmount()));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse approveInternally(UUID id, UUID approverUserId, UUID tenantId) {
        log.info("Aprovando internamente orçamento {} pelo usuário {} no tenant {}", id, approverUserId, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.INTERNAL_APPROVED);
        quote.setStatus(QuoteStatus.INTERNAL_APPROVED);
        quote.setApprovedByUserId(approverUserId);
        quote.setApprovedAt(OffsetDateTime.now());

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteApprovedInternallyEvent(saved.getId(), tenantId, saved.getUnitId(), approverUserId, saved.getTotalAmount()));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse sendToCustomer(UUID id, UUID tenantId) {
        log.info("Enviando orçamento {} ao cliente no tenant {}", id, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateCanSendToCustomer(quote.getStatus());
        QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.SENT_TO_CUSTOMER);

        quote.setStatus(QuoteStatus.SENT_TO_CUSTOMER);

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteSentToCustomerEvent(saved.getId(), tenantId, saved.getUnitId(), saved.getCustomerId(), saved.getTotalAmount()));

        return toResponse(saved, tenantId);
    }

    @Transactional
    public QuoteResponse processCustomerDecision(UUID id, CustomerDecisionRequest request, UUID tenantId) {
        log.info("Processando decisão do cliente para orçamento {}: aprovado={} no tenant {}", id, request.approved(), tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateCanProcessCustomerDecision(quote.getStatus());

        if (Boolean.TRUE.equals(request.approved())) {
            QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.CUSTOMER_APPROVED);
            quote.setStatus(QuoteStatus.CUSTOMER_APPROVED);
            quote.setCustomerApprovalStatus(CustomerApprovalStatus.APPROVED);
        } else {
            QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.CUSTOMER_REJECTED);
            quote.setStatus(QuoteStatus.CUSTOMER_REJECTED);
            quote.setCustomerApprovalStatus(CustomerApprovalStatus.REJECTED);
        }

        quote.setCustomerDecisionAt(OffsetDateTime.now());
        quote.setCustomerDecisionNotes(request.notes());

        Quote saved = quoteRepository.save(quote);
        eventBus.publish(new QuoteCustomerDecisionEvent(saved.getId(), tenantId, saved.getUnitId(), saved.getCustomerId(), quote.getCustomerApprovalStatus(), saved.getTotalAmount(), request.notes()));

        return toResponse(saved, tenantId);
    }

    @Transactional(readOnly = true)
    public QuoteResponse getQuoteById(UUID id, UUID tenantId) {
        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));
        return toResponse(quote, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<QuoteSummaryResponse> searchQuotes(UUID customerId, UUID vehicleId, QuoteStatus status, UUID unitId, Pageable pageable, UUID tenantId) {
        Specification<Quote> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Quote> page = quoteRepository.findAll(spec, pageable);
        return page.map(q -> toSummaryResponse(q, tenantId));
    }

    @Transactional
    public void cancelQuote(UUID id, String reason, UUID tenantId) {
        log.info("Cancelando orçamento {} no tenant {}", id, tenantId);

        Quote quote = quoteRepository.findByIdWithItems(id, tenantId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        QuoteLifecycleValidator.validateTransition(quote.getStatus(), QuoteStatus.CANCELED);
        quote.setStatus(QuoteStatus.CANCELED);

        quoteRepository.save(quote);
        quoteRepository.delete(quote); // soft delete via @SQLDelete

        eventBus.publish(new QuoteCanceledEvent(id, tenantId, quote.getUnitId(), reason));
    }

    private void applyTotalsToQuote(Quote quote, QuoteCalculator.CalculatedTotals totals) {
        quote.setSubtotalAmount(totals.subtotalAmount());
        quote.setDiscountAmount(totals.discountAmount());
        quote.setTaxAmount(totals.taxAmount());
        quote.setTotalLaborAmount(totals.totalLaborAmount());
        quote.setTotalPartsAmount(totals.totalPartsAmount());
        quote.setTotalAmount(totals.totalAmount());
    }

    private QuoteSummaryResponse toSummaryResponse(Quote quote, UUID tenantId) {
        CustomerSummaryResponse customer = crmContract.findCustomerSummary(quote.getCustomerId(), tenantId).orElse(null);
        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(quote.getVehicleId(), tenantId).orElse(null);
        int count = quote.getItems() != null ? quote.getItems().size() : 0;

        return new QuoteSummaryResponse(
                quote.getId(),
                quote.getUnitId(),
                quote.getCustomerId(),
                customer != null ? customer.name() : null,
                quote.getVehicleId(),
                vehicle != null ? vehicle.licensePlate() : null,
                vehicle != null ? vehicle.formattedLicensePlate() : null,
                vehicle != null ? vehicle.brand() : null,
                vehicle != null ? vehicle.model() : null,
                quote.getInspectionId(),
                quote.getStatus(),
                quote.getCustomerApprovalStatus(),
                quote.getTotalLaborAmount(),
                quote.getTotalPartsAmount(),
                quote.getTotalAmount(),
                count,
                quote.getValidUntil(),
                quote.getCreatedAt()
        );
    }

    private QuoteResponse toResponse(Quote quote, UUID tenantId) {
        CustomerSummaryResponse customer = crmContract.findCustomerSummary(quote.getCustomerId(), tenantId).orElse(null);
        VehicleSummaryResponse vehicle = crmContract.findVehicleSummary(quote.getVehicleId(), tenantId).orElse(null);

        List<QuoteItemResponse> itemResponses = quote.getItems() != null
                ? quote.getItems().stream().map(this::toItemResponse).toList()
                : List.of();

        return new QuoteResponse(
                quote.getId(),
                quote.getUnitId(),
                quote.getCustomerId(),
                customer != null ? customer.name() : null,
                customer != null ? customer.formattedDocument() : null,
                quote.getVehicleId(),
                vehicle != null ? vehicle.licensePlate() : null,
                vehicle != null ? vehicle.formattedLicensePlate() : null,
                vehicle != null ? vehicle.brand() : null,
                vehicle != null ? vehicle.model() : null,
                vehicle != null ? vehicle.year() : null,
                quote.getInspectionId(),
                quote.getAppointmentId(),
                quote.getCreatedByUserId(),
                quote.getApprovedByUserId(),
                quote.getApprovedAt(),
                quote.getStatus(),
                quote.getCustomerApprovalStatus(),
                quote.getCustomerDecisionAt(),
                quote.getCustomerDecisionNotes(),
                quote.getSubtotalAmount(),
                quote.getDiscountAmount(),
                quote.getTaxAmount(),
                quote.getTotalLaborAmount(),
                quote.getTotalPartsAmount(),
                quote.getTotalAmount(),
                quote.getValidUntil(),
                quote.getNotes(),
                quote.getTermsAndConditions(),
                itemResponses,
                quote.getCreatedAt(),
                quote.getUpdatedAt(),
                quote.getVersion()
        );
    }

    private QuoteItemResponse toItemResponse(QuoteItem item) {
        return new QuoteItemResponse(
                item.getId(),
                item.getQuote() != null ? item.getQuote().getId() : null,
                item.getType(),
                item.getProductId(),
                item.getName(),
                item.getDescription(),
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
}
