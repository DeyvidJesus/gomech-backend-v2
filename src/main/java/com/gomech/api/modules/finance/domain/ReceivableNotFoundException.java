package com.gomech.api.modules.finance.domain;

import java.util.UUID;

public class ReceivableNotFoundException extends RuntimeException {
    public ReceivableNotFoundException(UUID id) {
        super("Conta a receber não encontrada com o ID: " + id);
    }
}
