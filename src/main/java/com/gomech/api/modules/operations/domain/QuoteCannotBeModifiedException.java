package com.gomech.api.modules.operations.domain;

public class QuoteCannotBeModifiedException extends RuntimeException {
    public QuoteCannotBeModifiedException(QuoteStatus status) {
        super("O orçamento não pode ser modificado no status atual: " + status + ". Apenas orçamentos em DRAFT ou REVISION podem ser alterados.");
    }
}
