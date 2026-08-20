package com.gomech.api.modules.finance.domain;

public class InvalidFinanceOperationException extends RuntimeException {
    public InvalidFinanceOperationException(String message) {
        super(message);
    }
}
