package com.gomech.api.modules.operations.domain;

import java.util.Map;
import java.util.Set;

public final class QuoteLifecycleValidator {

    private static final Map<QuoteStatus, Set<QuoteStatus>> VALID_TRANSITIONS = Map.of(
            QuoteStatus.DRAFT, Set.of(QuoteStatus.PENDING_INTERNAL_APPROVAL, QuoteStatus.CANCELED),
            QuoteStatus.PENDING_INTERNAL_APPROVAL, Set.of(QuoteStatus.INTERNAL_APPROVED, QuoteStatus.REVISION, QuoteStatus.CANCELED),
            QuoteStatus.INTERNAL_APPROVED, Set.of(QuoteStatus.SENT_TO_CUSTOMER, QuoteStatus.REVISION, QuoteStatus.CANCELED),
            QuoteStatus.SENT_TO_CUSTOMER, Set.of(QuoteStatus.CUSTOMER_APPROVED, QuoteStatus.CUSTOMER_REJECTED, QuoteStatus.REVISION, QuoteStatus.EXPIRED, QuoteStatus.CANCELED),
            QuoteStatus.REVISION, Set.of(QuoteStatus.PENDING_INTERNAL_APPROVAL, QuoteStatus.DRAFT, QuoteStatus.CANCELED),
            QuoteStatus.CUSTOMER_REJECTED, Set.of(QuoteStatus.REVISION, QuoteStatus.CANCELED),
            QuoteStatus.EXPIRED, Set.of(QuoteStatus.REVISION, QuoteStatus.CANCELED),
            QuoteStatus.CUSTOMER_APPROVED, Set.of(),
            QuoteStatus.CANCELED, Set.of()
    );

    private QuoteLifecycleValidator() {
    }

    public static void validateTransition(QuoteStatus currentStatus, QuoteStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new IllegalArgumentException("Os status atual e de destino não podem ser nulos.");
        }

        if (currentStatus == targetStatus) {
            return;
        }

        Set<QuoteStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidQuoteStatusTransitionException(currentStatus, targetStatus);
        }
    }

    public static void validateCanModify(QuoteStatus currentStatus) {
        if (!currentStatus.isEditable()) {
            throw new QuoteCannotBeModifiedException(currentStatus);
        }
    }

    public static void validateCanSendToCustomer(QuoteStatus currentStatus) {
        if (currentStatus != QuoteStatus.INTERNAL_APPROVED) {
            throw new QuoteNotApprovedForSendingException(currentStatus);
        }
    }

    public static void validateCanProcessCustomerDecision(QuoteStatus currentStatus) {
        if (currentStatus != QuoteStatus.SENT_TO_CUSTOMER) {
            throw new InvalidQuoteStatusTransitionException("Decisão do cliente só pode ser registrada para orçamentos com status SENT_TO_CUSTOMER. Status atual: " + currentStatus);
        }
    }
}
