package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(UUID vehicleId) {
        super("Veículo não encontrado ou não pertence a esta oficina: " + vehicleId);
    }
}
