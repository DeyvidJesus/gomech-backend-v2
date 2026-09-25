package com.gomech.api.modules.ai.application;

import com.gomech.api.modules.ai.api.AiGatewayContract;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiGatewayContractImpl implements AiGatewayContract {

    private final AiGatewayService aiGatewayService;

    @Override
    public AiGatewayDtos.DiagnosticResponse diagnoseVehicle(
            UUID tenantId, UUID actorUserId, AiGatewayDtos.DiagnosticRequest request) {
        return aiGatewayService.diagnoseVehicle(tenantId, actorUserId, null, request);
    }

    @Override
    public AiGatewayDtos.QuoteProposalResponse generateQuoteProposal(
            UUID tenantId, UUID actorUserId, AiGatewayDtos.QuoteGenerationRequest request) {
        return aiGatewayService.generateQuoteProposal(tenantId, actorUserId, null, request);
    }

    @Override
    public AiGatewayDtos.WorkOrderSummaryResponse summarizeWorkOrder(
            UUID tenantId, UUID actorUserId, AiGatewayDtos.WorkOrderSummaryRequest request) {
        return aiGatewayService.summarizeWorkOrder(tenantId, actorUserId, null, request);
    }

    @Override
    public AiGatewayDtos.CustomerMessageDraftResponse draftCustomerMessage(
            UUID tenantId, UUID actorUserId, AiGatewayDtos.CustomerMessageDraftRequest request) {
        return aiGatewayService.draftCustomerMessage(tenantId, actorUserId, null, request);
    }

    @Override
    public AiGatewayDtos.AiUsageStatusResponse getUsageStatus(UUID tenantId) {
        return aiGatewayService.getUsageStatus(tenantId);
    }
}
