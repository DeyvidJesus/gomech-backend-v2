package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderLifecycleValidatorTest {

    @ParameterizedTest(name = "Transição válida de {0} para {1}")
    @CsvSource({
            "DRAFT, OPEN",
            "DRAFT, CANCELED",
            "OPEN, IN_PROGRESS",
            "OPEN, WAITING_PARTS",
            "OPEN, WAITING_CUSTOMER",
            "OPEN, CANCELED",
            "IN_PROGRESS, WAITING_PARTS",
            "IN_PROGRESS, WAITING_CUSTOMER",
            "IN_PROGRESS, COMPLETED",
            "IN_PROGRESS, CANCELED",
            "WAITING_PARTS, IN_PROGRESS",
            "WAITING_PARTS, WAITING_CUSTOMER",
            "WAITING_PARTS, CANCELED",
            "WAITING_CUSTOMER, IN_PROGRESS",
            "WAITING_CUSTOMER, WAITING_PARTS",
            "WAITING_CUSTOMER, CANCELED"
    })
    void shouldAllowValidTransitions(WorkOrderStatus from, WorkOrderStatus to) {
        assertDoesNotThrow(() -> WorkOrderLifecycleValidator.validateTransition(from, to));
    }

    @ParameterizedTest(name = "Transição inválida de {0} para {1}")
    @CsvSource({
            "DRAFT, IN_PROGRESS",
            "DRAFT, COMPLETED",
            "DRAFT, WAITING_PARTS",
            "OPEN, COMPLETED",
            "WAITING_PARTS, COMPLETED",
            "WAITING_CUSTOMER, COMPLETED",
            "COMPLETED, OPEN",
            "COMPLETED, IN_PROGRESS",
            "COMPLETED, CANCELED",
            "CANCELED, OPEN",
            "CANCELED, DRAFT",
            "CANCELED, COMPLETED"
    })
    void shouldRejectInvalidTransitions(WorkOrderStatus from, WorkOrderStatus to) {
        assertThrows(InvalidWorkOrderStatusTransitionException.class, () ->
                WorkOrderLifecycleValidator.validateTransition(from, to)
        );
    }

    @Test
    @DisplayName("Identificação correta de estados terminais")
    void shouldIdentifyTerminalStates() {
        assertTrue(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.COMPLETED));
        assertTrue(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.CANCELED));

        assertFalse(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.DRAFT));
        assertFalse(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.OPEN));
        assertFalse(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.IN_PROGRESS));
        assertFalse(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.WAITING_PARTS));
        assertFalse(WorkOrderLifecycleValidator.isTerminal(WorkOrderStatus.WAITING_CUSTOMER));
    }

    @Test
    @DisplayName("Permissão para modificar itens")
    void shouldCheckIfCanModifyItems() {
        assertTrue(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.DRAFT));
        assertTrue(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.OPEN));
        assertTrue(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.IN_PROGRESS));
        assertTrue(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.WAITING_PARTS));
        assertTrue(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.WAITING_CUSTOMER));

        assertFalse(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.COMPLETED));
        assertFalse(WorkOrderLifecycleValidator.canModifyItems(WorkOrderStatus.CANCELED));
    }
}
