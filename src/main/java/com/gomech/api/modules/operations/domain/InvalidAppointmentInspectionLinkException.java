package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class InvalidAppointmentInspectionLinkException extends RuntimeException {
    public InvalidAppointmentInspectionLinkException(UUID appointmentId, String reason) {
        super(String.format("Vínculo de agendamento inválido para a inspeção (Agendamento %s): %s", appointmentId, reason));
    }

    public InvalidAppointmentInspectionLinkException(String message) {
        super(message);
    }
}
