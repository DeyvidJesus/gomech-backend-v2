package com.gomech.api.modules.crm.domain;

public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String document) {
        super("Documento CPF ou CNPJ inválido: " + document);
    }
}
