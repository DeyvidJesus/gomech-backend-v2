package com.gomech.api.modules.tools.domain;

public class InvalidToolOperationException extends RuntimeException {
    public InvalidToolOperationException(String message) {
        super(message);
    }
}
