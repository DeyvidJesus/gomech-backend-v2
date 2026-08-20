package com.gomech.api.modules.inventory.domain;

public enum MovementReason {
    PURCHASE_ENTRY,
    WORK_ORDER_CONSUMPTION,
    TRANSFER_OUT,
    TRANSFER_IN,
    ADJUSTMENT_INCREASE,
    ADJUSTMENT_DECREASE,
    INITIAL_BALANCE,
    RETURN_ENTRY
}
