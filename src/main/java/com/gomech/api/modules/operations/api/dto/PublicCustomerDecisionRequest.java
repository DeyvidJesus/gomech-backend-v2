package com.gomech.api.modules.operations.api.dto;

import jakarta.validation.constraints.NotNull;

public record PublicCustomerDecisionRequest(
        @NotNull(message = "Decisão de aprovação é obrigatória")
        Boolean approved,
        String signerName,
        String signatureData,
        String notes
) {}
