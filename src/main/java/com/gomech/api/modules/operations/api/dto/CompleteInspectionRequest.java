package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.Valid;

import java.util.List;

public record CompleteInspectionRequest(
        String generalNotes,

        @Valid
        List<SaveInspectionItemRequest> finalItems
) {
}
