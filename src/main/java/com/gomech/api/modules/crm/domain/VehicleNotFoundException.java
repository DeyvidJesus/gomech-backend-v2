package com.gomech.api.modules.crm.domain;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(UUID id) {
        super("Veículo não encontrado para o identificador: " + id);
    }
}
