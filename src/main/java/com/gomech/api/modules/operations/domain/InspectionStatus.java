package com.gomech.api.modules.operations.domain;

public enum InspectionStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED;
    }
}
