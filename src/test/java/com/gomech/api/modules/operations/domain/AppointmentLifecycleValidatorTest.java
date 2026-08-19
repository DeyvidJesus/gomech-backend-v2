package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentLifecycleValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, CONFIRMED",
            "SCHEDULED, IN_PROGRESS",
            "SCHEDULED, CANCELED",
            "SCHEDULED, NO_SHOW",
            "CONFIRMED, IN_PROGRESS",
            "CONFIRMED, CANCELED",
            "CONFIRMED, NO_SHOW",
            "IN_PROGRESS, COMPLETED",
            "IN_PROGRESS, CANCELED",
            "SCHEDULED, SCHEDULED",
            "COMPLETED, COMPLETED"
    })
    @DisplayName("Deve permitir transições de status válidas")
    void shouldAllowValidTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(AppointmentLifecycleValidator.canTransition(from, to)).isTrue();
        AppointmentLifecycleValidator.validateTransition(from, to);
    }

    @ParameterizedTest
    @CsvSource({
            "COMPLETED, SCHEDULED",
            "COMPLETED, IN_PROGRESS",
            "CANCELED, SCHEDULED",
            "CANCELED, CONFIRMED",
            "NO_SHOW, IN_PROGRESS",
            "IN_PROGRESS, SCHEDULED",
            "IN_PROGRESS, CONFIRMED"
    })
    @DisplayName("Deve rejeitar transições de status inválidas e lançar exceção")
    void shouldRejectInvalidTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(AppointmentLifecycleValidator.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> AppointmentLifecycleValidator.validateTransition(from, to))
                .isInstanceOf(InvalidAppointmentStatusTransitionException.class);
    }

    @Test
    @DisplayName("Deve identificar estados terminais corretamente")
    void shouldIdentifyTerminalStates() {
        assertThat(AppointmentStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(AppointmentStatus.CANCELED.isTerminal()).isTrue();
        assertThat(AppointmentStatus.NO_SHOW.isTerminal()).isTrue();
        assertThat(AppointmentStatus.SCHEDULED.isTerminal()).isFalse();
        assertThat(AppointmentStatus.CONFIRMED.isTerminal()).isFalse();
        assertThat(AppointmentStatus.IN_PROGRESS.isTerminal()).isFalse();
    }
}
