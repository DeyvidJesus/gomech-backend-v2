package com.gomech.api.modules.crm.domain;

public class DuplicateLicensePlateException extends RuntimeException {
    public DuplicateLicensePlateException(String licensePlate) {
        super("Já existe um veículo ativo cadastrado com a placa: " + licensePlate);
    }
}
