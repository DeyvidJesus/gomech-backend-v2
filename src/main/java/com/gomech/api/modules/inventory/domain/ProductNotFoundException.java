package com.gomech.api.modules.inventory.domain;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super(String.format("Produto %s não encontrado.", productId));
        this.productId = productId;
    }

    public ProductNotFoundException(String message) {
        super(message);
        this.productId = null;
    }

    public UUID getProductId() {
        return productId;
    }
}
