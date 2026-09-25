package com.gomech.api.modules.ai.application;

import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;

import java.util.UUID;

public interface AiServiceClient {

    AiGatewayDtos.DiagnosticResponse executeDiagnosis(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.DiagnosticRequest request);

    AiGatewayDtos.QuoteProposalResponse executeQuoteGeneration(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.QuoteGenerationRequest request);

    AiGatewayDtos.WorkOrderSummaryResponse executeWorkOrderSummary(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.WorkOrderSummaryRequest request);

    AiGatewayDtos.CustomerMessageDraftResponse executeCustomerMessageDraft(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.CustomerMessageDraftRequest request);

    AiGatewayDtos.GeneralCompletionResponse executeGeneralCompletion(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.GeneralCompletionRequest request);
}
