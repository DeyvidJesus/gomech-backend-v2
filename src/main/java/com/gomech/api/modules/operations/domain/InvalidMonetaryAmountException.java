package com.gomech.api.modules.operations.domain;

public class InvalidMonetaryAmountException extends RuntimeException {
    public InvalidMonetaryAmountException(String message) {
        super(message);
    }
}
