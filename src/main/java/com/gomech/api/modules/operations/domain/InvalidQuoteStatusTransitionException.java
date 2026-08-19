package com.gomech.api.modules.operations.domain;

public class InvalidQuoteStatusTransitionException extends RuntimeException {
    public InvalidQuoteStatusTransitionException(QuoteStatus currentStatus, QuoteStatus targetStatus) {
        super("Transição de status inválida para o orçamento: de " + currentStatus + " para " + targetStatus);
    }

    public InvalidQuoteStatusTransitionException(String message) {
        super(message);
    }
}
