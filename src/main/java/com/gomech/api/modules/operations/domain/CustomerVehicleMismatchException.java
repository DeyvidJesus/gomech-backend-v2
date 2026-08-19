package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class CustomerVehicleMismatchException extends RuntimeException {
    public CustomerVehicleMismatchException(UUID customerId, UUID vehicleId) {
        super(String.format("Associação inválida: o veículo %s não pertence ao cliente %s ou um deles não foi encontrado no tenant ativo.", vehicleId, customerId));
    }
}
