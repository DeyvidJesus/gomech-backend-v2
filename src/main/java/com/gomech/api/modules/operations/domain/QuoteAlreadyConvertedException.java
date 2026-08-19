package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class QuoteAlreadyConvertedException extends RuntimeException {
    public QuoteAlreadyConvertedException(UUID quoteId, UUID existingWorkOrderId) {
        super(String.format("O orçamento %s já foi convertido na ordem de serviço %s.", quoteId, existingWorkOrderId));
    }
}
