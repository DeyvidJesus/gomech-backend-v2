package com.gomech.api.modules.crm.domain;

public class InvalidLicensePlateException extends RuntimeException {
    public InvalidLicensePlateException(String licensePlate) {
        super("Placa veicular em formato inválido: " + licensePlate);
    }
}
