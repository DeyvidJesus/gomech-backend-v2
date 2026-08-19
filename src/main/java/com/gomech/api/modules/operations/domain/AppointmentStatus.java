package com.gomech.api.modules.operations.domain;

public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELED,
    NO_SHOW;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED || this == NO_SHOW;
    }
}
