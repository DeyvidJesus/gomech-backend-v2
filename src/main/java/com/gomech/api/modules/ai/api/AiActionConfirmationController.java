package com.gomech.api.modules.ai.api;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.ai.api.dto.AiActionDtos;
import com.gomech.api.modules.ai.application.AiActionConfirmationService;
import com.gomech.api.modules.ai.domain.AiActionProposalStatus;
import com.gomech.api.modules.ai.domain.AiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/actions/proposals")
@RequiredArgsConstructor
@Slf4j
public class AiActionConfirmationController {

    private final AiActionConfirmationService confirmationService;
    private final ActorContextProvider actorContextProvider;

    @PostMapping
    @PreAuthorize("hasAuthority('AI_ACTION_PROPOSE') or hasAuthority('AI_ACTION_EXECUTE') or hasRole('Proprietário')")
    public ResponseEntity<AiActionDtos.AiActionProposalResponse> createProposal(
            @Valid @RequestBody AiActionDtos.CreateAiActionProposalRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = requireActor();
        UUID unitId = actor.unit() != null ? actor.unit().id() : null;

        AiActionDtos.AiActionProposalResponse response = confirmationService.createProposal(
                tenantId, unitId, actor.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AI_QUERY') or hasAuthority('AI_ACTION_CONFIRM') or hasRole('Proprietário')")
    public ResponseEntity<AiActionDtos.AiActionProposalResponse> getProposal(
            @PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        AiActionDtos.AiActionProposalResponse response = confirmationService.getProposal(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_QUERY') or hasAuthority('AI_ACTION_CONFIRM') or hasRole('Proprietário')")
    public ResponseEntity<Page<AiActionDtos.AiActionProposalResponse>> listProposals(
            @RequestParam(value = "status", required = false) AiActionProposalStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = requireActor();
        UUID unitId = actor.unit() != null ? actor.unit().id() : null;

        Page<AiActionDtos.AiActionProposalResponse> response = confirmationService.listProposals(
                tenantId, unitId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('AI_ACTION_CONFIRM') or hasRole('Proprietário')")
    public ResponseEntity<AiActionDtos.AiActionProposalResponse> confirmProposal(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) AiActionDtos.ConfirmAiActionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = requireActor();
        UUID unitId = actor.unit() != null ? actor.unit().id() : null;

        AiActionDtos.AiActionProposalResponse response = confirmationService.confirmAndExecute(
                tenantId, unitId, actor.userId(), id, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('AI_ACTION_CONFIRM') or hasRole('Proprietário')")
    public ResponseEntity<AiActionDtos.AiActionProposalResponse> rejectProposal(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AiActionDtos.RejectAiActionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = requireActor();
        UUID unitId = actor.unit() != null ? actor.unit().id() : null;

        AiActionDtos.AiActionProposalResponse response = confirmationService.rejectProposal(
                tenantId, unitId, actor.userId(), id, request);

        return ResponseEntity.ok(response);
    }

    private ActorContext requireActor() {
        return actorContextProvider.currentActor()
                .orElseThrow(() -> new AiException("Contexto de ator autenticado não encontrado.", "ACTOR_CONTEXT_NOT_FOUND"));
    }
}
