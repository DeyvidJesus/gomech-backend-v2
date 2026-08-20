package com.gomech.api.modules.inventory.domain;

import java.util.UUID;

public class StockReservationNotFoundException extends RuntimeException {

    private final UUID reservationId;

    public StockReservationNotFoundException(UUID reservationId) {
        super(String.format("Reserva de estoque %s não encontrada.", reservationId));
        this.reservationId = reservationId;
    }

    public UUID getReservationId() {
        return reservationId;
    }
}
