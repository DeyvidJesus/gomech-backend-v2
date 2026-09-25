package com.gomech.api.modules.operations.application;

import com.gomech.api.modules.operations.api.OperationsActionContract;
import com.gomech.api.modules.operations.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationsActionContractImpl implements OperationsActionContract {

    private final QuoteService quoteService;
    private final WorkOrderService workOrderService;
    private final AppointmentService appointmentService;

    @Override
    @Transactional
    public QuoteResponse applyProposedQuoteItems(
            UUID quoteId, UUID tenantId, UUID unitId, UUID userId, List<SaveQuoteItemRequest> items) {
        log.info("Executando aplicação de itens de orçamento confirmados por IA: quoteId={} tenantId={}", quoteId, tenantId);
        return quoteService.updateQuoteItems(quoteId, items, tenantId);
    }

    @Override
    @Transactional
    public WorkOrderResponse applyProposedWorkOrderItems(
            UUID workOrderId, UUID tenantId, UUID unitId, UUID userId, List<SaveWorkOrderItemRequest> items) {
        log.info("Executando aplicação de itens de OS confirmados por IA: workOrderId={} tenantId={}", workOrderId, tenantId);
        return workOrderService.updateWorkOrderItems(workOrderId, items, tenantId);
    }

    @Override
    @Transactional
    public AppointmentResponse scheduleAppointmentFromAiProposal(
            UUID tenantId, UUID unitId, CreateAppointmentRequest request) {
        log.info("Executando agendamento preventivo confirmado por IA: tenantId={} unitId={}", tenantId, unitId);
        return appointmentService.scheduleAppointment(request, tenantId, unitId);
    }
}
