package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class QuoteLifecycleValidatorTest {

    @ParameterizedTest(name = "Transição válida de {0} para {1}")
    @CsvSource({
            "DRAFT, PENDING_INTERNAL_APPROVAL",
            "DRAFT, CANCELED",
            "PENDING_INTERNAL_APPROVAL, INTERNAL_APPROVED",
            "PENDING_INTERNAL_APPROVAL, REVISION",
            "PENDING_INTERNAL_APPROVAL, CANCELED",
            "INTERNAL_APPROVED, SENT_TO_CUSTOMER",
            "INTERNAL_APPROVED, REVISION",
            "INTERNAL_APPROVED, CANCELED",
            "SENT_TO_CUSTOMER, CUSTOMER_APPROVED",
            "SENT_TO_CUSTOMER, CUSTOMER_REJECTED",
            "SENT_TO_CUSTOMER, REVISION",
            "SENT_TO_CUSTOMER, EXPIRED",
            "SENT_TO_CUSTOMER, CANCELED",
            "REVISION, PENDING_INTERNAL_APPROVAL",
            "REVISION, DRAFT",
            "REVISION, CANCELED",
            "CUSTOMER_REJECTED, REVISION",
            "CUSTOMER_REJECTED, CANCELED",
            "EXPIRED, REVISION",
            "EXPIRED, CANCELED"
    })
    void shouldAllowValidTransitions(QuoteStatus from, QuoteStatus to) {
        assertDoesNotThrow(() -> QuoteLifecycleValidator.validateTransition(from, to));
    }

    @ParameterizedTest(name = "Transição inválida de {0} para {1}")
    @CsvSource({
            "DRAFT, SENT_TO_CUSTOMER",
            "DRAFT, CUSTOMER_APPROVED",
            "PENDING_INTERNAL_APPROVAL, SENT_TO_CUSTOMER",
            "PENDING_INTERNAL_APPROVAL, CUSTOMER_APPROVED",
            "CUSTOMER_APPROVED, DRAFT",
            "CUSTOMER_APPROVED, REVISION",
            "CUSTOMER_APPROVED, CANCELED",
            "CANCELED, DRAFT",
            "CANCELED, PENDING_INTERNAL_APPROVAL"
    })
    void shouldRejectInvalidTransitions(QuoteStatus from, QuoteStatus to) {
        assertThrows(InvalidQuoteStatusTransitionException.class, () ->
                QuoteLifecycleValidator.validateTransition(from, to)
        );
    }

    @Test
    @DisplayName("Deve validar que apenas DRAFT e REVISION permitem modificação de itens")
    void shouldValidateCanModify() {
        assertDoesNotThrow(() -> QuoteLifecycleValidator.validateCanModify(QuoteStatus.DRAFT));
        assertDoesNotThrow(() -> QuoteLifecycleValidator.validateCanModify(QuoteStatus.REVISION));

        assertThrows(QuoteCannotBeModifiedException.class, () ->
                QuoteLifecycleValidator.validateCanModify(QuoteStatus.PENDING_INTERNAL_APPROVAL)
        );
        assertThrows(QuoteCannotBeModifiedException.class, () ->
                QuoteLifecycleValidator.validateCanModify(QuoteStatus.INTERNAL_APPROVED)
        );
        assertThrows(QuoteCannotBeModifiedException.class, () ->
                QuoteLifecycleValidator.validateCanModify(QuoteStatus.SENT_TO_CUSTOMER)
        );
        assertThrows(QuoteCannotBeModifiedException.class, () ->
                QuoteLifecycleValidator.validateCanModify(QuoteStatus.CUSTOMER_APPROVED)
        );
    }

    @Test
    @DisplayName("Deve exigir status INTERNAL_APPROVED para envio ao cliente")
    void shouldRequireInternalApprovalBeforeSending() {
        assertDoesNotThrow(() -> QuoteLifecycleValidator.validateCanSendToCustomer(QuoteStatus.INTERNAL_APPROVED));

        assertThrows(QuoteNotApprovedForSendingException.class, () ->
                QuoteLifecycleValidator.validateCanSendToCustomer(QuoteStatus.DRAFT)
        );
        assertThrows(QuoteNotApprovedForSendingException.class, () ->
                QuoteLifecycleValidator.validateCanSendToCustomer(QuoteStatus.PENDING_INTERNAL_APPROVAL)
        );
        assertThrows(QuoteNotApprovedForSendingException.class, () ->
                QuoteLifecycleValidator.validateCanSendToCustomer(QuoteStatus.REVISION)
        );
    }

    @Test
    @DisplayName("Deve validar que decisão do cliente requer status SENT_TO_CUSTOMER")
    void shouldRequireSentToCustomerForCustomerDecision() {
        assertDoesNotThrow(() -> QuoteLifecycleValidator.validateCanProcessCustomerDecision(QuoteStatus.SENT_TO_CUSTOMER));

        assertThrows(InvalidQuoteStatusTransitionException.class, () ->
                QuoteLifecycleValidator.validateCanProcessCustomerDecision(QuoteStatus.DRAFT)
        );
        assertThrows(InvalidQuoteStatusTransitionException.class, () ->
                QuoteLifecycleValidator.validateCanProcessCustomerDecision(QuoteStatus.INTERNAL_APPROVED)
        );
    }
}
