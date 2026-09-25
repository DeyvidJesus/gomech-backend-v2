package com.gomech.api.modules.ai.application;

import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.logging.CorrelationId;
import com.gomech.api.core.tenancy.UnitReference;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.domain.*;
import com.gomech.api.modules.ai.events.AiRequestAuditedEvent;
import com.gomech.api.modules.ai.infrastructure.metrics.AiGatewayMetrics;
import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiGatewayAuditLog;
import com.gomech.api.modules.ai.infrastructure.persistence.repository.AiGatewayAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGatewayService {

    private final AiServiceClient aiServiceClient;
    private final AiGatewayAuditLogRepository auditLogRepository;
    private final AiGatewayMetrics metrics;
    private final EntitlementService entitlementService;
    private final AuditRecorder auditRecorder;
    private final DomainEventBus eventBus;


    @Transactional
    public AiGatewayDtos.DiagnosticResponse diagnoseVehicle(
            UUID tenantId, UUID actorUserId, UUID unitId, AiGatewayDtos.DiagnosticRequest request) {
        String correlationId = resolveCorrelationId();
        AiCapability capability = AiCapability.DIAGNOSTIC_ASSIST;

        // Check the quota before invoking the provider.
        enforcePreInvocationQuota(tenantId, capability, correlationId);

        // Redact sensitive data before sending context to the AI provider.
        String sanitizedSymptoms = SensitiveDataSanitizer.sanitize(request.symptomsDescription());
        String sanitizedNotes = SensitiveDataSanitizer.sanitize(request.customerNotes());
        AiGatewayDtos.DiagnosticRequest sanitizedRequest = AiGatewayDtos.DiagnosticRequest.builder()
                .vehicleId(request.vehicleId())
                .vehicleMake(request.vehicleMake())
                .vehicleModel(request.vehicleModel())
                .vehicleYear(request.vehicleYear())
                .odometerKm(request.odometerKm())
                .symptomsDescription(sanitizedSymptoms)
                .faultCodes(request.faultCodes())
                .customerNotes(sanitizedNotes)
                .build();

        try {
            AiGatewayDtos.DiagnosticResponse response = aiServiceClient.executeDiagnosis(
                    tenantId, actorUserId, unitId, correlationId, sanitizedRequest);

            postExecutionSuccess(
                    tenantId, unitId, actorUserId, correlationId, capability,
                    response.usage().modelUsed(), response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens(),
                    response.usage().latencyMs(),
                    sanitizedSymptoms,
                    response.diagnosisSummary()
            );

            return response;
        } catch (Exception ex) {
            postExecutionError(tenantId, unitId, actorUserId, correlationId, capability, ex, sanitizedSymptoms);
            throw ex;
        }
    }


    @Transactional
    public AiGatewayDtos.QuoteProposalResponse generateQuoteProposal(
            UUID tenantId, UUID actorUserId, UUID unitId, AiGatewayDtos.QuoteGenerationRequest request) {
        String correlationId = resolveCorrelationId();
        AiCapability capability = AiCapability.QUOTE_GENERATION;

        enforcePreInvocationQuota(tenantId, capability, correlationId);

        String sanitizedDiag = SensitiveDataSanitizer.sanitize(request.diagnosticSummary());
        String sanitizedVeh = SensitiveDataSanitizer.sanitize(request.vehicleInfo());
        AiGatewayDtos.QuoteGenerationRequest sanitizedRequest = AiGatewayDtos.QuoteGenerationRequest.builder()
                .workOrderId(request.workOrderId())
                .vehicleInfo(sanitizedVeh)
                .diagnosticSummary(sanitizedDiag)
                .customerBudgetLimit(request.customerBudgetLimit())
                .build();

        try {
            AiGatewayDtos.QuoteProposalResponse response = aiServiceClient.executeQuoteGeneration(
                    tenantId, actorUserId, unitId, correlationId, sanitizedRequest);

            postExecutionSuccess(
                    tenantId, unitId, actorUserId, correlationId, capability,
                    response.usage().modelUsed(), response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens(),
                    response.usage().latencyMs(),
                    sanitizedDiag,
                    response.summary()
            );

            return response;
        } catch (Exception ex) {
            postExecutionError(tenantId, unitId, actorUserId, correlationId, capability, ex, sanitizedDiag);
            throw ex;
        }
    }


    @Transactional
    public AiGatewayDtos.WorkOrderSummaryResponse summarizeWorkOrder(
            UUID tenantId, UUID actorUserId, UUID unitId, AiGatewayDtos.WorkOrderSummaryRequest request) {
        String correlationId = resolveCorrelationId();
        AiCapability capability = AiCapability.WORK_ORDER_SUMMARY;

        enforcePreInvocationQuota(tenantId, capability, correlationId);

        String sanitizedReported = SensitiveDataSanitizer.sanitize(request.customerReportedIssues());
        String sanitizedNotes = SensitiveDataSanitizer.sanitize(request.mechanicNotes());
        AiGatewayDtos.WorkOrderSummaryRequest sanitizedRequest = AiGatewayDtos.WorkOrderSummaryRequest.builder()
                .workOrderId(request.workOrderId())
                .orderNumber(request.orderNumber())
                .vehicleSummary(request.vehicleSummary())
                .customerReportedIssues(sanitizedReported)
                .servicesPerformed(request.servicesPerformed())
                .partsReplaced(request.partsReplaced())
                .mechanicNotes(sanitizedNotes)
                .build();

        try {
            AiGatewayDtos.WorkOrderSummaryResponse response = aiServiceClient.executeWorkOrderSummary(
                    tenantId, actorUserId, unitId, correlationId, sanitizedRequest);

            postExecutionSuccess(
                    tenantId, unitId, actorUserId, correlationId, capability,
                    response.usage().modelUsed(), response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens(),
                    response.usage().latencyMs(),
                    sanitizedReported,
                    response.technicalSummary()
            );

            return response;
        } catch (Exception ex) {
            postExecutionError(tenantId, unitId, actorUserId, correlationId, capability, ex, sanitizedReported);
            throw ex;
        }
    }


    @Transactional
    public AiGatewayDtos.CustomerMessageDraftResponse draftCustomerMessage(
            UUID tenantId, UUID actorUserId, UUID unitId, AiGatewayDtos.CustomerMessageDraftRequest request) {
        String correlationId = resolveCorrelationId();
        AiCapability capability = AiCapability.CUSTOMER_MESSAGE_DRAFT;

        enforcePreInvocationQuota(tenantId, capability, correlationId);

        String sanitizedName = SensitiveDataSanitizer.sanitize(request.customerName());
        String sanitizedDetails = SensitiveDataSanitizer.sanitize(request.keyDetails());
        AiGatewayDtos.CustomerMessageDraftRequest sanitizedRequest = AiGatewayDtos.CustomerMessageDraftRequest.builder()
                .customerId(request.customerId())
                .customerName(sanitizedName)
                .vehiclePlate(request.vehiclePlate())
                .topic(request.topic())
                .keyDetails(sanitizedDetails)
                .tone(request.tone())
                .build();

        try {
            AiGatewayDtos.CustomerMessageDraftResponse response = aiServiceClient.executeCustomerMessageDraft(
                    tenantId, actorUserId, unitId, correlationId, sanitizedRequest);

            postExecutionSuccess(
                    tenantId, unitId, actorUserId, correlationId, capability,
                    response.usage().modelUsed(), response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens(),
                    response.usage().latencyMs(),
                    sanitizedDetails,
                    response.bodyMessage()
            );

            return response;
        } catch (Exception ex) {
            postExecutionError(tenantId, unitId, actorUserId, correlationId, capability, ex, sanitizedDetails);
            throw ex;
        }
    }


    @Transactional
    public AiGatewayDtos.GeneralCompletionResponse executeGeneralCompletion(
            UUID tenantId, UUID actorUserId, UUID unitId, AiGatewayDtos.GeneralCompletionRequest request) {
        String correlationId = resolveCorrelationId();
        AiCapability capability = AiCapability.GENERAL_COMPLETION;

        enforcePreInvocationQuota(tenantId, capability, correlationId);

        String sanitizedPrompt = SensitiveDataSanitizer.sanitize(request.prompt());
        AiGatewayDtos.GeneralCompletionRequest sanitizedRequest = AiGatewayDtos.GeneralCompletionRequest.builder()
                .prompt(sanitizedPrompt)
                .model(request.model())
                .maxTokens(request.maxTokens())
                .temperature(request.temperature())
                .build();

        try {
            AiGatewayDtos.GeneralCompletionResponse response = aiServiceClient.executeGeneralCompletion(
                    tenantId, actorUserId, unitId, correlationId, sanitizedRequest);

            postExecutionSuccess(
                    tenantId, unitId, actorUserId, correlationId, capability,
                    response.usage().modelUsed(), response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens(),
                    response.usage().latencyMs(),
                    sanitizedPrompt,
                    response.completionText()
            );

            return response;
        } catch (Exception ex) {
            postExecutionError(tenantId, unitId, actorUserId, correlationId, capability, ex, sanitizedPrompt);
            throw ex;
        }
    }


    @Transactional(readOnly = true)
    public AiGatewayDtos.AiUsageStatusResponse getUsageStatus(UUID tenantId) {
        QuotaDecision decision = entitlementService.checkQuota(tenantId, QuotaDimension.AI_USAGE, 1);
        return AiGatewayDtos.AiUsageStatusResponse.builder()
                .tenantId(tenantId)
                .planCode("PRO")
                .quotaLimit(5000L)
                .quotaUsed(decision.allowed() ? 120L : 5000L)
                .quotaRemaining(decision.allowed() ? 4880L : 0L)
                .isAllowed(decision.allowed())
                .resetDate(OffsetDateTime.now().plusDays(10))
                .build();
    }


    private void enforcePreInvocationQuota(UUID tenantId, AiCapability capability, String correlationId) {
        QuotaDecision quotaDecision = entitlementService.checkQuota(tenantId, QuotaDimension.AI_USAGE, 1);
        if (!quotaDecision.allowed()) {
            log.warn("AI Gateway rejeitou requisição por cota excedida: tenant={} capability={} motivo={}",
                    tenantId, capability, quotaDecision.reason());

            metrics.recordError(capability.name(), "REJECTED_QUOTA_EXCEEDED", "AI_QUOTA_EXCEEDED");

                        AiGatewayAuditLog rejectedLog = AiGatewayAuditLog.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .correlationId(correlationId)
                    .capability(capability.name())
                    .model("NONE")
                    .promptTokens(0)
                    .completionTokens(0)
                    .totalTokens(0)
                    .latencyMs(0L)
                    .status(AiGatewayStatus.REJECTED_QUOTA_EXCEEDED.name())
                    .errorCode("AI_QUOTA_EXCEEDED")
                    .redactedPromptSummary("Requisição bloqueada antes da invocação do modelo")
                    .redactedResponseSummary("Cota de IA esgotada: " + quotaDecision.reason())
                    .createdAt(OffsetDateTime.now())
                    .build();
            auditLogRepository.save(rejectedLog);

            throw new AiQuotaExceededException("Limite de utilização de Inteligência Artificial excedido para o seu plano: " + quotaDecision.reason());
        }
    }

    private void postExecutionSuccess(
            UUID tenantId, UUID unitId, UUID userId, String correlationId, AiCapability capability,
            String model, int promptTokens, int completionTokens, int totalTokens, long latencyMs,
            String promptSummary, String responseSummary) {

        // Charge usage only after a successful execution.
        entitlementService.recordUsage(tenantId, QuotaDimension.AI_USAGE, 1);

        // Persist audit data under the active RLS scope.
        AiGatewayAuditLog auditLog = AiGatewayAuditLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .userId(userId)
                .correlationId(correlationId)
                .capability(capability.name())
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latencyMs)
                .status(AiGatewayStatus.SUCCESS.name())
                .redactedPromptSummary(SensitiveDataSanitizer.summarize(promptSummary, 500))
                .redactedResponseSummary(SensitiveDataSanitizer.summarize(responseSummary, 500))
                .createdAt(OffsetDateTime.now())
                .build();
        auditLogRepository.save(auditLog);

                if (userId != null) {
            UnitReference unitRef = unitId != null ? UnitReference.of(unitId) : null;
            ActorContext actor = new ActorContext(userId, tenantId, unitRef, java.util.Set.of(), java.util.Set.of());
            auditRecorder.record(actor, new AuditRecordRequest(
                    "ai.gateway.invoked", "ai", auditLog.getId().toString(),
                    Map.of(
                            "capability", capability.name(),
                            "model", model,
                            "tokens", String.valueOf(totalTokens),
                            "latencyMs", String.valueOf(latencyMs),
                            "correlationId", correlationId != null ? correlationId : ""
                    )
            ));
        }

                eventBus.publish(new AiRequestAuditedEvent(
                tenantId, unitId, userId, correlationId, capability, model,
                promptTokens, completionTokens, totalTokens, latencyMs,
                AiGatewayStatus.SUCCESS, null
        ));

                metrics.recordSuccess(capability.name(), model, promptTokens, completionTokens, latencyMs);
    }

    private void postExecutionError(
            UUID tenantId, UUID unitId, UUID userId, String correlationId, AiCapability capability,
            Exception ex, String promptSummary) {

        String errorCode = (ex instanceof AiException ae) ? ae.getErrorCode() : "AI_EXECUTION_FAILED";
        String status = (ex instanceof AiRateLimitException) ? AiGatewayStatus.RATE_LIMITED.name() : AiGatewayStatus.FAILED.name();

        AiGatewayAuditLog errorLog = AiGatewayAuditLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .userId(userId)
                .correlationId(correlationId)
                .capability(capability.name())
                .model("UNKNOWN")
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .latencyMs(0L)
                .status(status)
                .errorCode(errorCode)
                .redactedPromptSummary(SensitiveDataSanitizer.summarize(promptSummary, 500))
                .redactedResponseSummary(SensitiveDataSanitizer.summarize(ex.getMessage(), 500))
                .createdAt(OffsetDateTime.now())
                .build();
        auditLogRepository.save(errorLog);

        metrics.recordError(capability.name(), status, errorCode);
    }

    private String resolveCorrelationId() {
        String corr = CorrelationId.current();
        return (corr != null && !corr.isBlank()) ? corr : UUID.randomUUID().toString();
    }
}
