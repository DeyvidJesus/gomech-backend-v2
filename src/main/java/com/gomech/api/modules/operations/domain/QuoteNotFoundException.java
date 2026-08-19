package com.gomech.api.modules.operations.domain;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {
    public QuoteNotFoundException(UUID id) {
        super("Orçamento não encontrado com o ID: " + id);
    }
}
