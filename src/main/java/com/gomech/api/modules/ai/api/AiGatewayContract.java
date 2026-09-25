package com.gomech.api.modules.ai.api;

import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;

import java.util.UUID;

public interface AiGatewayContract {

    AiGatewayDtos.DiagnosticResponse diagnoseVehicle(UUID tenantId, UUID actorUserId, AiGatewayDtos.DiagnosticRequest request);

    AiGatewayDtos.QuoteProposalResponse generateQuoteProposal(UUID tenantId, UUID actorUserId, AiGatewayDtos.QuoteGenerationRequest request);

    AiGatewayDtos.WorkOrderSummaryResponse summarizeWorkOrder(UUID tenantId, UUID actorUserId, AiGatewayDtos.WorkOrderSummaryRequest request);

    AiGatewayDtos.CustomerMessageDraftResponse draftCustomerMessage(UUID tenantId, UUID actorUserId, AiGatewayDtos.CustomerMessageDraftRequest request);

    AiGatewayDtos.AiUsageStatusResponse getUsageStatus(UUID tenantId);
}
