package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeWorkOrderStatusRequest(
        @NotNull(message = "O novo status é obrigatório")
        WorkOrderStatus status,

        String notes
) {
}
