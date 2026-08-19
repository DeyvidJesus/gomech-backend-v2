package com.gomech.api.modules.operations.domain;

public class QuoteNotApprovedForSendingException extends RuntimeException {
    public QuoteNotApprovedForSendingException(QuoteStatus currentStatus) {
        super("O orçamento não pode ser enviado ao cliente no status " + currentStatus + ". É obrigatória a aprovação interna prévia pelo administrador/gerente (status INTERNAL_APPROVED).");
    }
}
