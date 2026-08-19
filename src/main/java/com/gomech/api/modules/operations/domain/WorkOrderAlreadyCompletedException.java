package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class WorkOrderAlreadyCompletedException extends RuntimeException {
    public WorkOrderAlreadyCompletedException(UUID id) {
        super(String.format("A ordem de serviço %s já foi finalizada e não pode ser modificada.", id));
    }
}
