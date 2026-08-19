package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class InspectionNotFoundException extends RuntimeException {
    public InspectionNotFoundException(UUID id) {
        super("Inspeção veicular não encontrada com o identificador: " + id);
    }
}
