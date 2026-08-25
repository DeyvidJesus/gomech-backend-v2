package com.gomech.api.modules.operations.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.operations.api.dto.PublicCustomerDecisionRequest;
import com.gomech.api.modules.operations.api.dto.PublicQuoteItemResponse;
import com.gomech.api.modules.operations.api.dto.PublicQuoteResponse;
import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteLifecycleValidator;
import com.gomech.api.modules.operations.domain.QuoteNotFoundException;
import com.gomech.api.modules.operations.domain.QuoteStatus;
import com.gomech.api.modules.operations.events.QuoteCustomerDecisionEvent;
import com.gomech.api.modules.operations.infrastructure.persistence.model.Quote;
import com.gomech.api.modules.operations.infrastructure.persistence.repository.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicQuoteService {

    private static final Logger log = LoggerFactory.getLogger(PublicQuoteService.class);

    private final QuoteRepository quoteRepository;
    private final CrmContract crmContract;
    private final DomainEventBus eventBus;

    public PublicQuoteService(QuoteRepository quoteRepository,
                              CrmContract crmContract,
                              DomainEventBus eventBus) {
        this.quoteRepository = quoteRepository;
        this.crmContract = crmContract;
        this.eventBus = eventBus;
    }

    @Transactional(readOnly = true)
    public PublicQuoteResponse getPublicQuote(UUID quoteId) {
        log.info("Buscando orçamento público {}", quoteId);

        Quote quote = quoteRepository.findByIdWithItems(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));

        return toPublicResponse(quote);
    }

    @Transactional
    public PublicQuoteResponse processPublicDecision(UUID quoteId, PublicCustomerDecisionRequest request) {
        log.info("Processando decisão pública do cliente para orçamento {}: aprovado={}", quoteId, request.approved());

        Quote quote = quoteRepository.findByIdWithItems(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));

        QuoteStatus targetStatus = Boolean.TRUE.equals(request.approved())
                ? QuoteStatus.CUSTOMER_APPROVED
                : QuoteStatus.CUSTOMER_REJECTED;

        QuoteLifecycleValidator.validateTransition(quote.getStatus(), targetStatus);

        CustomerApprovalStatus approvalStatus = Boolean.TRUE.equals(request.approved())
                ? CustomerApprovalStatus.APPROVED
                : CustomerApprovalStatus.REJECTED;

        quote.setStatus(targetStatus);
        quote.setCustomerApprovalStatus(approvalStatus);
        quote.setCustomerDecisionAt(OffsetDateTime.now());

        StringBuilder notesBuilder = new StringBuilder();
        if (request.signerName() != null && !request.signerName().isBlank()) {
            notesBuilder.append("Assinado por: ").append(request.signerName().trim()).append("\n");
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            notesBuilder.append("Observações: ").append(request.notes().trim()).append("\n");
        }
        if (request.signatureData() != null && !request.signatureData().isBlank()) {
            notesBuilder.append("[Assinatura Digital Capturada]\n");
        }
        quote.setCustomerDecisionNotes(notesBuilder.toString().trim());

        Quote saved = quoteRepository.save(quote);

        eventBus.publish(new QuoteCustomerDecisionEvent(
                saved.getId(),
                saved.getTenantId(),
                saved.getUnitId(),
                saved.getCustomerId(),
                approvalStatus,
                saved.getTotalAmount(),
                saved.getCustomerDecisionNotes()
        ));

        return toPublicResponse(saved);
    }

    private PublicQuoteResponse toPublicResponse(Quote quote) {
        Optional<CustomerSummaryResponse> customerOpt = crmContract.findCustomerSummary(quote.getCustomerId(), quote.getTenantId());
        Optional<VehicleSummaryResponse> vehicleOpt = crmContract.findVehicleSummary(quote.getVehicleId(), quote.getTenantId());

        String customerName = customerOpt.map(CustomerSummaryResponse::name).orElse("Cliente");
        String customerPhone = customerOpt.map(CustomerSummaryResponse::phone).orElse(null);
        String customerEmail = customerOpt.map(CustomerSummaryResponse::email).orElse(null);

        String vehiclePlate = vehicleOpt.map(VehicleSummaryResponse::licensePlate).orElse(null);
        String vehicleModel = vehicleOpt.map(v -> (v.brand() != null ? v.brand() + " " : "") + v.model()).orElse(null);
        Integer vehicleYear = vehicleOpt.map(VehicleSummaryResponse::year).orElse(null);

        List<PublicQuoteItemResponse> items = quote.getItems().stream()
                .map(item -> new PublicQuoteItemResponse(
                        item.getId(),
                        item.getType(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getDiscountAmount(),
                        item.getTotalAmount()
                ))
                .toList();

        return new PublicQuoteResponse(
                quote.getId(),
                quote.getStatus(),
                quote.getCustomerApprovalStatus(),
                "Oficina GoMech",
                null,
                customerName,
                customerPhone,
                customerEmail,
                vehiclePlate,
                vehicleModel,
                vehicleYear,
                quote.getSubtotalAmount(),
                quote.getDiscountAmount(),
                quote.getTaxAmount(),
                quote.getTotalLaborAmount(),
                quote.getTotalPartsAmount(),
                quote.getTotalAmount(),
                quote.getValidUntil(),
                quote.getNotes(),
                quote.getTermsAndConditions(),
                quote.getCustomerDecisionNotes(),
                quote.getCustomerDecisionAt(),
                items,
                quote.getCreatedAt()
        );
    }
}
