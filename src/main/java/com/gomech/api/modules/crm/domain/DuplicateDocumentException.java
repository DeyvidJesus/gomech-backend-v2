package com.gomech.api.modules.crm.domain;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String document) {
        super("Já existe um cliente ativo cadastrado com o documento: " + document);
    }
}
