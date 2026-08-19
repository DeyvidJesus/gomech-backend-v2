package com.gomech.api.modules.operations.domain;

public class InvalidAppointmentStatusTransitionException extends RuntimeException {
    public InvalidAppointmentStatusTransitionException(AppointmentStatus from, AppointmentStatus to) {
        super(String.format("Transição de status inválida para o agendamento: não é permitido alterar de %s para %s.", from, to));
    }
}
