package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class InspectionAlreadyCompletedException extends RuntimeException {
    public InspectionAlreadyCompletedException(UUID id) {
        super(String.format("A inspeção %s já foi finalizada ou cancelada e não pode mais ser modificada.", id));
    }
}
