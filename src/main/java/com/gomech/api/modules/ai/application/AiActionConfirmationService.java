package com.gomech.api.modules.ai.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.tenancy.UnitReference;
import com.gomech.api.modules.ai.api.dto.AiActionDtos;
import com.gomech.api.modules.ai.domain.*;
import com.gomech.api.modules.ai.events.AiActionExecutedEvent;
import com.gomech.api.modules.ai.events.AiActionProposedEvent;
import com.gomech.api.modules.ai.events.AiActionRejectedEvent;
import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiActionProposal;
import com.gomech.api.modules.ai.infrastructure.persistence.repository.AiActionProposalRepository;
import com.gomech.api.modules.operations.api.OperationsActionContract;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.api.dto.SaveQuoteItemRequest;
import com.gomech.api.modules.operations.api.dto.SaveWorkOrderItemRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiActionConfirmationService {

    private final AiActionProposalRepository proposalRepository;
    private final OperationsActionContract operationsActionContract;
    private final AuditRecorder auditRecorder;
    private final DomainEventBus eventBus;
    private final ObjectMapper objectMapper;


    @Transactional
    public AiActionDtos.AiActionProposalResponse createProposal(
            UUID tenantId, UUID unitId, UUID actorUserId, AiActionDtos.CreateAiActionProposalRequest request) {
        log.info("Criando proposta de ação de IA: tenantId={} actionType={} actor={}",
                tenantId, request.actionType(), actorUserId);

        int ttl = (request.ttlMinutes() != null && request.ttlMinutes() > 0) ? request.ttlMinutes() : 30;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(ttl);

        AiActionProposal proposal = AiActionProposal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .actorUserId(actorUserId)
                .actionType(request.actionType())
                .targetResourceType(request.targetResourceType())
                .targetResourceId(request.targetResourceId())
                .title(request.title())
                .summary(request.summary())
                .payloadJson(request.payloadJson())
                .status(AiActionProposalStatus.PENDING)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        AiActionProposal saved = proposalRepository.save(proposal);

                eventBus.publish(new AiActionProposedEvent(
                saved.getId(), tenantId, unitId, actorUserId,
                saved.getActionType(), saved.getTargetResourceType(),
                saved.getTargetResourceId(), saved.getExpiresAt(), saved.getCreatedAt()
        ));

        return mapToResponse(saved);
    }


    @Transactional(readOnly = true)
    public AiActionDtos.AiActionProposalResponse getProposal(UUID tenantId, UUID proposalId) {
        AiActionProposal proposal = proposalRepository.findByIdAndTenantId(proposalId, tenantId)
                .orElseThrow(() -> new AiException("Proposta de ação não encontrada: " + proposalId, "AI_ACTION_NOT_FOUND"));

        return mapToResponse(proposal);
    }


    @Transactional(readOnly = true)
    public Page<AiActionDtos.AiActionProposalResponse> listProposals(
            UUID tenantId, UUID unitId, AiActionProposalStatus status, Pageable pageable) {
        return proposalRepository.searchProposals(tenantId, unitId, status, pageable)
                .map(this::mapToResponse);
    }


    @Transactional
    public AiActionDtos.AiActionProposalResponse confirmAndExecute(
            UUID tenantId, UUID unitId, UUID actorUserId, UUID proposalId, AiActionDtos.ConfirmAiActionRequest request) {
        log.info("Confirmando e executando proposta de IA: proposalId={} tenantId={} actor={}",
                proposalId, tenantId, actorUserId);

        AiActionProposal proposal = proposalRepository.findByIdAndTenantId(proposalId, tenantId)
                .orElseThrow(() -> new AiException("Proposta de ação não encontrada: " + proposalId, "AI_ACTION_NOT_FOUND"));

        // A proposal can only be executed once.
        if (proposal.getStatus() != AiActionProposalStatus.PENDING) {
            throw new AiActionAlreadyProcessedException(
                    "Esta proposta já foi processada anteriormente com o status: " + proposal.getStatus());
        }

        // Expired proposals cannot be executed.
        if (proposal.isExpired()) {
            proposal.setStatus(AiActionProposalStatus.EXPIRED);
            proposal.setUpdatedAt(OffsetDateTime.now());
            proposalRepository.save(proposal);
            throw new AiActionExpiredException("A proposta de ação expirou em " + proposal.getExpiresAt() + " e não pode ser executada.");
        }

        String effectivePayloadJson = (request != null && request.adjustedPayloadJson() != null && !request.adjustedPayloadJson().isBlank())
                ? request.adjustedPayloadJson()
                : proposal.getPayloadJson();

        String executionResultJson;
        try {
            executionResultJson = executeDomainCommand(
                    proposal.getActionType(), proposal.getTargetResourceId(),
                    tenantId, unitId, actorUserId, effectivePayloadJson);
        } catch (Exception ex) {
            log.error("Falha na execução de domínio da ação {}: {}", proposal.getId(), ex.getMessage(), ex);
            proposal.setStatus(AiActionProposalStatus.FAILED);
            proposal.setExecutionResultJson("{\"error\": \"" + ex.getMessage() + "\"}");
            proposal.setUpdatedAt(OffsetDateTime.now());
            proposalRepository.save(proposal);
            throw new AiException("Falha ao executar ação nos comandos de negócio: " + ex.getMessage(), ex, "AI_ACTION_EXECUTION_FAILED");
        }

        OffsetDateTime now = OffsetDateTime.now();
        proposal.setStatus(AiActionProposalStatus.EXECUTED);
        proposal.setConfirmedByUserId(actorUserId);
        proposal.setConfirmedAt(now);
        proposal.setExecutedAt(now);
        proposal.setExecutionResultJson(executionResultJson);
        proposal.setUpdatedAt(now);
        AiActionProposal saved = proposalRepository.save(proposal);

        UnitReference unitRef = unitId != null ? UnitReference.of(unitId) : null;
        ActorContext actor = new ActorContext(actorUserId, tenantId, unitRef, java.util.Set.of(), java.util.Set.of());
        auditRecorder.record(actor, new AuditRecordRequest(
                "ai.action.confirmed_and_executed", "ai_action_proposal", saved.getId().toString(),
                Map.of(
                        "actionType", saved.getActionType().name(),
                        "targetResourceType", saved.getTargetResourceType() != null ? saved.getTargetResourceType() : "",
                        "targetResourceId", saved.getTargetResourceId() != null ? saved.getTargetResourceId().toString() : "",
                        "notes", (request != null && request.confirmationNotes() != null) ? request.confirmationNotes() : ""
                )
        ));

        eventBus.publish(new AiActionExecutedEvent(
                saved.getId(), tenantId, unitId, actorUserId,
                saved.getActionType(), saved.getTargetResourceType(),
                saved.getTargetResourceId(), now
        ));

        return mapToResponse(saved);
    }


    @Transactional
    public AiActionDtos.AiActionProposalResponse rejectProposal(
            UUID tenantId, UUID unitId, UUID actorUserId, UUID proposalId, AiActionDtos.RejectAiActionRequest request) {
        log.info("Rejeitando proposta de ação de IA: proposalId={} tenantId={} actor={} reason={}",
                proposalId, tenantId, actorUserId, request.rejectionReason());

        AiActionProposal proposal = proposalRepository.findByIdAndTenantId(proposalId, tenantId)
                .orElseThrow(() -> new AiException("Proposta de ação não encontrada: " + proposalId, "AI_ACTION_NOT_FOUND"));

        if (proposal.getStatus() != AiActionProposalStatus.PENDING) {
            throw new AiActionAlreadyProcessedException(
                    "Esta proposta não está pendente e não pode ser rejeitada. Status atual: " + proposal.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        proposal.setStatus(AiActionProposalStatus.REJECTED);
        proposal.setRejectionReason(request.rejectionReason());
        proposal.setConfirmedByUserId(actorUserId);
        proposal.setConfirmedAt(now);
        proposal.setUpdatedAt(now);
        AiActionProposal saved = proposalRepository.save(proposal);

        UnitReference unitRef = unitId != null ? UnitReference.of(unitId) : null;
        ActorContext actor = new ActorContext(actorUserId, tenantId, unitRef, java.util.Set.of(), java.util.Set.of());
        auditRecorder.record(actor, new AuditRecordRequest(
                "ai.action.rejected", "ai_action_proposal", saved.getId().toString(),
                Map.of(
                        "actionType", saved.getActionType().name(),
                        "rejectionReason", request.rejectionReason()
                )
        ));

        eventBus.publish(new AiActionRejectedEvent(
                saved.getId(), tenantId, unitId, actorUserId,
                saved.getActionType(), request.rejectionReason(), now
        ));

        return mapToResponse(saved);
    }


    private String executeDomainCommand(
            AiActionType actionType, UUID targetResourceId,
            UUID tenantId, UUID unitId, UUID userId, String payloadJson) throws Exception {

        switch (actionType) {
            case APPLY_QUOTE_ITEMS:
                List<SaveQuoteItemRequest> quoteItems = objectMapper.readValue(
                        payloadJson, new TypeReference<List<SaveQuoteItemRequest>>() {});
                Object quoteRes = operationsActionContract.applyProposedQuoteItems(
                        targetResourceId, tenantId, unitId, userId, quoteItems);
                return objectMapper.writeValueAsString(quoteRes);

            case APPLY_WORK_ORDER_ITEMS:
                List<SaveWorkOrderItemRequest> woItems = objectMapper.readValue(
                        payloadJson, new TypeReference<List<SaveWorkOrderItemRequest>>() {});
                Object woRes = operationsActionContract.applyProposedWorkOrderItems(
                        targetResourceId, tenantId, unitId, userId, woItems);
                return objectMapper.writeValueAsString(woRes);

            case SCHEDULE_PREVENTIVE_APPOINTMENT:
                CreateAppointmentRequest apptReq = objectMapper.readValue(
                        payloadJson, CreateAppointmentRequest.class);
                Object apptRes = operationsActionContract.scheduleAppointmentFromAiProposal(
                        tenantId, unitId, apptReq);
                return objectMapper.writeValueAsString(apptRes);

            case DRAFT_MESSAGE:
            case PROPOSE_CHECKLIST:
            case PROPOSE_QUOTE_ITEMS:
            default:
                return "{\"status\": \"CONFIRMED_AND_LOGGED\", \"timestamp\": \"" + OffsetDateTime.now() + "\"}";
        }
    }

    private AiActionDtos.AiActionProposalResponse mapToResponse(AiActionProposal p) {
        return AiActionDtos.AiActionProposalResponse.builder()
                .id(p.getId())
                .tenantId(p.getTenantId())
                .unitId(p.getUnitId())
                .actorUserId(p.getActorUserId())
                .actionType(p.getActionType())
                .targetResourceType(p.getTargetResourceType())
                .targetResourceId(p.getTargetResourceId())
                .title(p.getTitle())
                .summary(p.getSummary())
                .payloadJson(p.getPayloadJson())
                .status(p.getStatus())
                .rejectionReason(p.getRejectionReason())
                .expiresAt(p.getExpiresAt())
                .isExpired(p.isExpired())
                .confirmedByUserId(p.getConfirmedByUserId())
                .confirmedAt(p.getConfirmedAt())
                .executedAt(p.getExecutedAt())
                .executionResultJson(p.getExecutionResultJson())
                .version(p.getVersion())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
