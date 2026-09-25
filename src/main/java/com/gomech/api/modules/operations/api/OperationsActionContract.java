package com.gomech.api.modules.operations.api;

import com.gomech.api.modules.operations.api.dto.*;

import java.util.List;
import java.util.UUID;

/**
 * Public contract exposed by the Operations module for executing confirmed AI actions.
 * Follows ADR-002: cross-module communication happens exclusively through public API contracts.
 */
public interface OperationsActionContract {

    QuoteResponse applyProposedQuoteItems(
            UUID quoteId, UUID tenantId, UUID unitId, UUID userId, List<SaveQuoteItemRequest> items);

    WorkOrderResponse applyProposedWorkOrderItems(
            UUID workOrderId, UUID tenantId, UUID unitId, UUID userId, List<SaveWorkOrderItemRequest> items);

    AppointmentResponse scheduleAppointmentFromAiProposal(
            UUID tenantId, UUID unitId, CreateAppointmentRequest request);
}
