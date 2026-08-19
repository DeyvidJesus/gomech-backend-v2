package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class WorkOrderNotFoundException extends RuntimeException {
    public WorkOrderNotFoundException(UUID id) {
        super(String.format("Ordem de serviço não encontrada com o ID: %s", id));
    }
}
