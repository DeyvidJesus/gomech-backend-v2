package com.gomech.api.modules.crm.domain;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(UUID id) {
        super("Cliente não encontrado para o identificador: " + id);
    }
}
