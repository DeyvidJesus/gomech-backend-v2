package com.gomech.api.modules.inventory.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    private final UUID productId;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientStockException(UUID productId, BigDecimal requested, BigDecimal available) {
        super(String.format("Estoque insuficiente para o produto %s. Solicitado: %s, Disponível: %s",
                productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
