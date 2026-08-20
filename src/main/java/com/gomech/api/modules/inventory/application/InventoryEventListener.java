package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.domain.ReservationStatus;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.StockReservation;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final StockConsumptionService consumptionService;
    private final StockReservationRepository reservationRepository;

    /**
     * Consome assincronamente as reservas ativas da Ordem de Serviço concluída.
     */
    @Async
    @EventListener
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        log.info("Evento WorkOrderCompletedEvent recebido no módulo Inventory para OS {}", event.workOrderId());

        try {
            List<StockReservation> activeReservations = reservationRepository.findAllByTenantIdAndWorkOrderIdAndStatus(
                event.tenantId(),
                event.workOrderId(),
                ReservationStatus.CREATED
            );

            for (StockReservation res : activeReservations) {
                String idempotencyKey = "WO_CONSUME_" + event.workOrderId() + "_" + res.getWorkOrderItemId();
                consumptionService.consumeWorkOrderItem(
                    event.tenantId(),
                    event.unitId(),
                    event.workOrderId(),
                    res.getWorkOrderItemId(),
                    res.getProductId(),
                    res.getQuantity(),
                    event.mechanicUserId(),
                    idempotencyKey
                );
            }
        } catch (Exception e) {
            log.error("Erro ao processar consumo de estoque para OS {}: {}", event.workOrderId(), e.getMessage(), e);
        }
    }
}
