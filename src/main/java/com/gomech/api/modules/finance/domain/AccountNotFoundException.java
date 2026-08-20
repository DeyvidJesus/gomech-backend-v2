package com.gomech.api.modules.finance.domain;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID id) {
        super("Conta financeira não encontrada com o ID: " + id);
    }
}
