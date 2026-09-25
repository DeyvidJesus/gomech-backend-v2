package com.gomech.api.modules.ai.api;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.application.AiGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Gateway", description = "Ponto único e autenticado de acesso a capacidades de Inteligência Artificial")
public class AiGatewayController {

    private final AiGatewayService aiGatewayService;
    private final ActorContextProvider actorContextProvider;

    @PostMapping("/diagnose")
    @PreAuthorize("hasAuthority('AI_QUERY') or hasRole('Proprietário')")
    @Operation(summary = "Diagnóstico assistido de anomalias veiculares", description = "Analisa sintomas e códigos de falha para inferir causas e propor plano de inspeção.")
    public ResponseEntity<AiGatewayDtos.DiagnosticResponse> diagnose(
            @Valid @RequestBody AiGatewayDtos.DiagnosticRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = actorContextProvider.currentActor().orElse(null);
        UUID userId = actor != null ? actor.userId() : null;
        UUID unitId = (actor != null && actor.unit() != null) ? actor.unit().id() : null;

        AiGatewayDtos.DiagnosticResponse response = aiGatewayService.diagnoseVehicle(tenantId, userId, unitId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-quote-items")
    @PreAuthorize("hasAuthority('AI_ACTION_EXECUTE') or hasRole('Proprietário')")
    @Operation(summary = "Proposição automática de itens de orçamento", description = "Gera estimativa e lista estruturada de peças e mão de obra a partir de um diagnóstico.")
    public ResponseEntity<AiGatewayDtos.QuoteProposalResponse> generateQuoteItems(
            @Valid @RequestBody AiGatewayDtos.QuoteGenerationRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = actorContextProvider.currentActor().orElse(null);
        UUID userId = actor != null ? actor.userId() : null;
        UUID unitId = (actor != null && actor.unit() != null) ? actor.unit().id() : null;

        AiGatewayDtos.QuoteProposalResponse response = aiGatewayService.generateQuoteProposal(tenantId, userId, unitId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/summarize-work-order")
    @PreAuthorize("hasAuthority('AI_QUERY') or hasRole('Proprietário')")
    @Operation(summary = "Sumarização técnica e executiva de Ordem de Serviço", description = "Produz laudos técnicos e resumos executivos amigáveis para entrega ao cliente.")
    public ResponseEntity<AiGatewayDtos.WorkOrderSummaryResponse> summarizeWorkOrder(
            @Valid @RequestBody AiGatewayDtos.WorkOrderSummaryRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = actorContextProvider.currentActor().orElse(null);
        UUID userId = actor != null ? actor.userId() : null;
        UUID unitId = (actor != null && actor.unit() != null) ? actor.unit().id() : null;

        AiGatewayDtos.WorkOrderSummaryResponse response = aiGatewayService.summarizeWorkOrder(tenantId, userId, unitId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft-message")
    @PreAuthorize("hasAuthority('AI_ACTION_EXECUTE') or hasRole('Proprietário')")
    @Operation(summary = "Redação assistida de comunicados ao cliente", description = "Gera rascunho de mensagens em canais WhatsApp/Email com tom personalizado.")
    public ResponseEntity<AiGatewayDtos.CustomerMessageDraftResponse> draftMessage(
            @Valid @RequestBody AiGatewayDtos.CustomerMessageDraftRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = actorContextProvider.currentActor().orElse(null);
        UUID userId = actor != null ? actor.userId() : null;
        UUID unitId = (actor != null && actor.unit() != null) ? actor.unit().id() : null;

        AiGatewayDtos.CustomerMessageDraftResponse response = aiGatewayService.draftCustomerMessage(tenantId, userId, unitId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/completions")
    @PreAuthorize("hasAuthority('AI_QUERY') or hasRole('Proprietário')")
    @Operation(summary = "Processamento genérico com IA", description = "Executa prompt sanitizado com o modelo especificado sob governança do gateway.")
    public ResponseEntity<AiGatewayDtos.GeneralCompletionResponse> complete(
            @Valid @RequestBody AiGatewayDtos.GeneralCompletionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ActorContext actor = actorContextProvider.currentActor().orElse(null);
        UUID userId = actor != null ? actor.userId() : null;
        UUID unitId = (actor != null && actor.unit() != null) ? actor.unit().id() : null;

        AiGatewayDtos.GeneralCompletionResponse response = aiGatewayService.executeGeneralCompletion(tenantId, userId, unitId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('AI_QUERY') or hasRole('Proprietário')")
    @Operation(summary = "Consultar consumo e saldo de cota de IA", description = "Retorna o status atual de consumo da cota de inteligência artificial do tenant.")
    public ResponseEntity<AiGatewayDtos.AiUsageStatusResponse> getUsageStatus() {
        UUID tenantId = TenantContextHolder.getTenantId();
        AiGatewayDtos.AiUsageStatusResponse response = aiGatewayService.getUsageStatus(tenantId);
        return ResponseEntity.ok(response);
    }
}
