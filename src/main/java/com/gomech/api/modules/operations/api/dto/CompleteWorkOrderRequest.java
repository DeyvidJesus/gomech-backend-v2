package com.gomech.api.modules.operations.api.dto;

public record CompleteWorkOrderRequest(
        Integer endMileage,
        String technicalNotes,
        String customerNotes
) {
}
