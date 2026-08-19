package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.constraints.NotNull;

public record CustomerDecisionRequest(
        @NotNull(message = "A decisão de aprovação é obrigatória (true para aprovado, false para rejeitado).")
        Boolean approved,

        String notes
) {
}
