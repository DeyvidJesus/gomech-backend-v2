package com.gomech.api.modules.operations.domain;

public enum InspectionItemStatus {
    OK,
    ATTENTION,
    CRITICAL,
    NOT_APPLICABLE;

    public boolean requiresAction() {
        return this == ATTENTION || this == CRITICAL;
    }
}
