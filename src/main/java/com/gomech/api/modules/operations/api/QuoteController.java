package com.gomech.api.modules.operations.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.application.QuoteService;
import com.gomech.api.modules.operations.domain.QuoteStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Operations Quotes", description = "Gestão de orçamentos, cálculo de peças e mão de obra e fluxo de dupla aprovação")
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QuoteController {

    private final QuoteService quoteService;

    @Operation(summary = "Criar novo orçamento", description = "Cria um orçamento avulso ou associado a um agendamento/vistoria técnica.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orçamento criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
            @ApiResponse(responseCode = "422", description = "Veículo não pertence ao cliente ou inconsistência de vínculos")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticationPrincipal String userId
    ) {
        UUID creatorId = userId != null ? UUID.fromString(userId) : null;
        QuoteResponse response = quoteService.createQuote(
                request,
                TenantContextHolder.getTenantId(),
                request.unitId(),
                creatorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Gerar orçamento a partir de vistoria técnica", description = "Importa automaticamente itens apontados como ATENÇÃO ou CRÍTICO em uma vistoria técnica.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orçamento gerado a partir da vistoria"),
            @ApiResponse(responseCode = "404", description = "Vistoria não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PostMapping("/from-inspection/{inspectionId}")
    public ResponseEntity<QuoteResponse> createQuoteFromInspection(
            @PathVariable UUID inspectionId,
            @AuthenticationPrincipal String userId
    ) {
        UUID creatorId = userId != null ? UUID.fromString(userId) : null;
        QuoteResponse response = quoteService.createQuoteFromInspection(
                inspectionId,
                TenantContextHolder.getTenantId(),
                null,
                creatorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Buscar orçamentos paginados", description = "Filtra orçamentos por cliente, veículo, status ou unidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de orçamentos")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<PageResponse<QuoteSummaryResponse>> searchQuotes(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<QuoteSummaryResponse> result = quoteService.searchQuotes(
                customerId,
                vehicleId,
                status,
                unitId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(summary = "Obter detalhes do orçamento", description = "Retorna todos os dados, itens calculados e histórico de aprovação do orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes do orçamento"),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<QuoteResponse> getQuoteById(@PathVariable UUID id) {
        QuoteResponse response = quoteService.getQuoteById(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Atualizar dados gerais do orçamento", description = "Atualiza validade, observações e termos enquanto o orçamento estiver em DRAFT ou REVISION.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento atualizado com sucesso"),
            @ApiResponse(responseCode = "422", description = "Orçamento não pode ser editado no status atual")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}")
    public ResponseEntity<QuoteResponse> updateQuote(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuoteRequest request
    ) {
        QuoteResponse response = quoteService.updateQuote(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Atualizar itens e recalcular orçamento", description = "Substitui a lista de peças e serviços com recálculo determinístico de subtotal, descontos, impostos e total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Itens atualizados e totais recalculados"),
            @ApiResponse(responseCode = "422", description = "Valores monetários inválidos ou orçamento bloqueado")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}/items")
    public ResponseEntity<QuoteResponse> updateQuoteItems(
            @PathVariable UUID id,
            @Valid @RequestBody List<SaveQuoteItemRequest> items
    ) {
        QuoteResponse response = quoteService.updateQuoteItems(id, items, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submeter orçamento para aprovação interna", description = "Muda o status para PENDING_INTERNAL_APPROVAL para revisão do gerente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento submetido para aprovação interna"),
            @ApiResponse(responseCode = "422", description = "Transição inválida")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PostMapping("/{id}/submit-approval")
    public ResponseEntity<QuoteResponse> submitForInternalApproval(@PathVariable UUID id) {
        QuoteResponse response = quoteService.submitForInternalApproval(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Aprovar orçamento internamente (Gerência/Admin)", description = "Aprova o orçamento internamente, habilitando o envio ao cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento aprovado internamente com sucesso"),
            @ApiResponse(responseCode = "422", description = "Transição de status inválida")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_APPROVE') or hasRole('Proprietário')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<QuoteResponse> approveInternally(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId
    ) {
        UUID approverId = userId != null ? UUID.fromString(userId) : null;
        QuoteResponse response = quoteService.approveInternally(id, approverId, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Enviar orçamento ao cliente", description = "Muda o status para SENT_TO_CUSTOMER. Requer aprovação interna prévia (INTERNAL_APPROVED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento marcado como enviado ao cliente"),
            @ApiResponse(responseCode = "422", description = "Orçamento não aprovado internamente ou transição inválida")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_SEND') or hasRole('Proprietário')")
    @PostMapping("/{id}/send")
    public ResponseEntity<QuoteResponse> sendToCustomer(@PathVariable UUID id) {
        QuoteResponse response = quoteService.sendToCustomer(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Registrar decisão do cliente", description = "Registra a aprovação (CUSTOMER_APPROVED) ou rejeição (CUSTOMER_REJECTED) pelo cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decisão do cliente registrada"),
            @ApiResponse(responseCode = "422", description = "Orçamento não está aguardando decisão do cliente")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_WRITE') or hasRole('Proprietário')")
    @PostMapping("/{id}/customer-decision")
    public ResponseEntity<QuoteResponse> processCustomerDecision(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerDecisionRequest request
    ) {
        QuoteResponse response = quoteService.processCustomerDecision(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancelar orçamento", description = "Cancela o orçamento (soft delete e mudança para CANCELED).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Orçamento cancelado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_QUOTE_CANCEL') or hasRole('Proprietário')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelQuote(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Cancelado pelo usuário") String reason
    ) {
        quoteService.cancelQuote(id, reason, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
