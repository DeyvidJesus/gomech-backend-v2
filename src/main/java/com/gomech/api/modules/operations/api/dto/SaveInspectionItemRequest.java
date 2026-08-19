package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.InspectionCategory;
import com.gomech.api.modules.operations.domain.InspectionItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveInspectionItemRequest(
        UUID id,

        @NotNull(message = "A categoria do item é obrigatória")
        InspectionCategory category,

        @NotBlank(message = "O nome do item é obrigatório")
        @Size(max = 150, message = "O nome do item deve ter no máximo 150 caracteres")
        String name,

        @NotNull(message = "O status do item é obrigatório")
        InspectionItemStatus status,

        String notes,

        String recommendedAction,

        String photoUrls
) {
}
