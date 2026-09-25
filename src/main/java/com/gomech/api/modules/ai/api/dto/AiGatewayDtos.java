package com.gomech.api.modules.ai.api.dto;

import com.gomech.api.modules.ai.domain.AiActionType;
import com.gomech.api.modules.ai.domain.AiModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AiGatewayDtos {

    private AiGatewayDtos() {}

    // ------------------------------------------------------------------------
    // DIAGNOSTIC CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record DiagnosticRequest(
            UUID vehicleId,
            String vehicleMake,
            String vehicleModel,
            Integer vehicleYear,
            Integer odometerKm,
            @NotBlank(message = "Descrição dos sintomas é obrigatória")
            @Size(max = 2000, message = "Sintomas não podem exceder 2000 caracteres")
            String symptomsDescription,
            List<String> faultCodes, // Ex: P0300, P0420
            String customerNotes
    ) {}

    @Builder
    public record DiagnosticResponse(
            String diagnosisSummary,
            List<String> probableCauses,
            List<String> recommendedInspectionSteps,
            Double confidenceScore,
            AiActionType proposedAction,
            List<String> proposedChecklist,
            UsageMetadata usage
    ) {}

    // ------------------------------------------------------------------------
    // QUOTE GENERATION CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record QuoteGenerationRequest(
            UUID workOrderId,
            String vehicleInfo,
            @NotBlank(message = "Resumo do diagnóstico é obrigatório")
            String diagnosticSummary,
            BigDecimal customerBudgetLimit
    ) {}

    @Builder
    public record QuoteProposalResponse(
            String summary,
            List<ProposedQuoteItemDto> proposedItems,
            BigDecimal estimatedTotalLabor,
            BigDecimal estimatedTotalParts,
            BigDecimal totalEstimate,
            UsageMetadata usage
    ) {}

    @Builder
    public record ProposedQuoteItemDto(
            String type, // SERVICE, PART
            String description,
            String partNumber,
            BigDecimal quantity,
            BigDecimal estimatedPrice,
            String rationale
    ) {}

    // ------------------------------------------------------------------------
    // WORK ORDER SUMMARY CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record WorkOrderSummaryRequest(
            UUID workOrderId,
            String orderNumber,
            String vehicleSummary,
            String customerReportedIssues,
            List<String> servicesPerformed,
            List<String> partsReplaced,
            String mechanicNotes
    ) {}

    @Builder
    public record WorkOrderSummaryResponse(
            String technicalSummary,
            String executiveCustomerSummary,
            List<String> preventiveRecommendations,
            String warrantyTerms,
            UsageMetadata usage
    ) {}

    // ------------------------------------------------------------------------
    // CUSTOMER MESSAGE DRAFT CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record CustomerMessageDraftRequest(
            UUID customerId,
            String customerName,
            String vehiclePlate,
            @NotBlank(message = "Tópico da mensagem é obrigatório")
            String topic, // QUOTE_READY, SERVICE_COMPLETED, ADDITIONAL_APPROVAL_NEEDED, INVOICE_READY
            String keyDetails,
            String tone // CORDIAL, TECHNICAL, FORMAL
    ) {}

    @Builder
    public record CustomerMessageDraftResponse(
            String channel, // WHATSAPP, EMAIL, SMS
            String subject,
            String bodyMessage,
            UsageMetadata usage
    ) {}

    // ------------------------------------------------------------------------
    // GENERAL COMPLETION CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record GeneralCompletionRequest(
            @NotBlank(message = "Prompt não pode ser vazio")
            @Size(max = 4000, message = "Prompt não pode exceder 4000 caracteres")
            String prompt,
            AiModelType model,
            Integer maxTokens,
            Double temperature
    ) {}

    @Builder
    public record GeneralCompletionResponse(
            String completionText,
            String finishReason,
            UsageMetadata usage
    ) {}

    // ------------------------------------------------------------------------
    // QUOTA & OBSERVABILITY CONTRACTS
    // ------------------------------------------------------------------------

    @Builder
    public record AiUsageStatusResponse(
            UUID tenantId,
            String planCode,
            long quotaLimit,
            long quotaUsed,
            long quotaRemaining,
            boolean isAllowed,
            OffsetDateTime resetDate
    ) {}

    @Builder
    public record UsageMetadata(
            String modelUsed,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs
    ) {}
}
