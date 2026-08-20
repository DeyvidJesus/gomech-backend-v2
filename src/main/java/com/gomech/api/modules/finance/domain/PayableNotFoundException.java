package com.gomech.api.modules.finance.domain;

import java.util.UUID;

public class PayableNotFoundException extends RuntimeException {
    public PayableNotFoundException(UUID id) {
        super("Conta a pagar não encontrada com o ID: " + id);
    }
}
