package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException(UUID id) {
        super("Agendamento não encontrado com o identificador: " + id);
    }
}
