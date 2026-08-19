package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class QuoteNotEligibleForWorkOrderException extends RuntimeException {
    public QuoteNotEligibleForWorkOrderException(UUID quoteId, QuoteStatus status) {
        super(String.format("O orçamento %s não está elegível para conversão em ordem de serviço. Status atual: '%s'. Apenas orçamentos aprovados pelo cliente podem ser convertidos.", quoteId, status));
    }
}
