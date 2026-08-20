package com.gomech.api.modules.inventory.domain;

public class InvalidStockTransferException extends RuntimeException {

    public InvalidStockTransferException(String message) {
        super(message);
    }
}
