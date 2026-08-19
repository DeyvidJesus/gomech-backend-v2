package com.gomech.api.modules.operations.domain;

import java.time.OffsetDateTime;

public class InvalidCalendarRangeException extends RuntimeException {
    public InvalidCalendarRangeException(OffsetDateTime from, OffsetDateTime to) {
        super(String.format("Intervalo de calendário inválido: a data inicial (%s) deve ser anterior ou igual à data final (%s).", from, to));
    }

    public InvalidCalendarRangeException(String message) {
        super(message);
    }
}
