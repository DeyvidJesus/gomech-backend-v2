package com.gomech.api.modules.operations.domain;

public class InvalidWorkOrderStatusTransitionException extends RuntimeException {
    public InvalidWorkOrderStatusTransitionException(WorkOrderStatus currentStatus, WorkOrderStatus targetStatus) {
        super(String.format("Transição de status inválida para a ordem de serviço: de '%s' para '%s'", currentStatus, targetStatus));
    }
}
