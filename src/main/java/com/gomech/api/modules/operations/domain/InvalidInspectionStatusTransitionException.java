package com.gomech.api.modules.operations.domain;

public class InvalidInspectionStatusTransitionException extends RuntimeException {
    public InvalidInspectionStatusTransitionException(InspectionStatus from, InspectionStatus to) {
        super(String.format("Transição de status inválida para a inspeção: não é permitido alterar de %s para %s.", from, to));
    }
}
