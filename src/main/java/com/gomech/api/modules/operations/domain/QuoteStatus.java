package com.gomech.api.modules.operations.domain;

public enum QuoteStatus {
    DRAFT,
    PENDING_INTERNAL_APPROVAL,
    INTERNAL_APPROVED,
    SENT_TO_CUSTOMER,
    CUSTOMER_APPROVED,
    CUSTOMER_REJECTED,
    REVISION,
    EXPIRED,
    CANCELED;

    public boolean isEditable() {
        return this == DRAFT || this == REVISION;
    }

    public boolean isTerminal() {
        return this == CUSTOMER_APPROVED || this == CANCELED || this == EXPIRED;
    }
}
