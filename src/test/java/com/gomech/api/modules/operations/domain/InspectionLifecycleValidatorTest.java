package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class InspectionLifecycleValidatorTest {

    @ParameterizedTest(name = "Transição {0} -> {1} deve ser permitida: {2}")
    @CsvSource({
            "IN_PROGRESS, COMPLETED, true",
            "IN_PROGRESS, CANCELED, true",
            "IN_PROGRESS, IN_PROGRESS, true",
            "COMPLETED, COMPLETED, true",
            "CANCELED, CANCELED, true",
            "COMPLETED, IN_PROGRESS, false",
            "COMPLETED, CANCELED, false",
            "CANCELED, IN_PROGRESS, false",
            "CANCELED, COMPLETED, false"
    })
    @DisplayName("Validação de transições de status do ciclo de vida da inspeção")
    void shouldValidateTransitionsCorrectly(InspectionStatus from, InspectionStatus to, boolean expectedAllowed) {
        boolean actual = InspectionLifecycleValidator.canTransition(from, to);
        assertEquals(expectedAllowed, actual);

        if (expectedAllowed) {
            assertDoesNotThrow(() -> InspectionLifecycleValidator.validateTransition(from, to));
        } else {
            assertThrows(InvalidInspectionStatusTransitionException.class,
                    () -> InspectionLifecycleValidator.validateTransition(from, to));
        }
    }

    @Test
    @DisplayName("Transição com status nulo deve retornar falso e lançar exceção")
    void shouldHandleNullStatus() {
        assertFalse(InspectionLifecycleValidator.canTransition(null, InspectionStatus.IN_PROGRESS));
        assertFalse(InspectionLifecycleValidator.canTransition(InspectionStatus.IN_PROGRESS, null));
        assertThrows(InvalidInspectionStatusTransitionException.class,
                () -> InspectionLifecycleValidator.validateTransition(null, InspectionStatus.IN_PROGRESS));
    }
}
