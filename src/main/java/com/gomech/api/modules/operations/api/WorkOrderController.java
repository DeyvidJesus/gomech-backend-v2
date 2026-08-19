package com.gomech.api.modules.operations.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.application.WorkOrderService;
import com.gomech.api.modules.operations.domain.WorkOrderStatus;
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

@Tag(name = "Operations Work Orders", description = "Gestão do ciclo de vida de ordens de serviço, checklist técnico, peças, serviços e quadro Kanban")
@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "Criar nova ordem de serviço", description = "Cria uma ordem de serviço avulsa com serviços e peças associadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordem de serviço criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
            @ApiResponse(responseCode = "422", description = "Veículo não pertence ao cliente ou dados inconsistentes")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request,
            @AuthenticationPrincipal String userId
    ) {
        UUID creatorId = userId != null ? UUID.fromString(userId) : null;
        WorkOrderResponse response = workOrderService.createWorkOrder(
                request,
                TenantContextHolder.getTenantId(),
                request.unitId(),
                creatorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Converter orçamento aprovado em ordem de serviço", description = "Cria uma ordem de serviço copiando integralmente peças e serviços do orçamento aprovado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordem de serviço criada com sucesso a partir do orçamento"),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado"),
            @ApiResponse(responseCode = "422", description = "Orçamento não aprovado pelo cliente ou já convertido anteriormente")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @PostMapping("/from-quote/{quoteId}")
    public ResponseEntity<WorkOrderResponse> createFromQuote(
            @PathVariable UUID quoteId,
            @RequestParam(required = false) UUID unitId,
            @AuthenticationPrincipal String userId
    ) {
        UUID creatorId = userId != null ? UUID.fromString(userId) : null;
        WorkOrderResponse response = workOrderService.createFromQuote(
                quoteId,
                TenantContextHolder.getTenantId(),
                unitId,
                creatorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar ordens de serviço com paginação e filtros", description = "Permite filtrar por cliente, veículo, mecânico, status e unidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada retornada com sucesso")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<PageResponse<WorkOrderSummaryResponse>> searchWorkOrders(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID mechanicId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkOrderSummaryResponse> result = workOrderService.searchWorkOrders(
                customerId,
                vehicleId,
                mechanicId,
                status,
                unitId,
                pageRequest,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(summary = "Consultar quadro Kanban de ordens de serviço", description = "Retorna as ordens de serviço ativas da unidade agrupadas por colunas do Kanban.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quadro Kanban retornado com sucesso")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @GetMapping("/kanban")
    public ResponseEntity<WorkOrderKanbanResponse> getKanbanBoard(
            @RequestParam(required = false) UUID unitId
    ) {
        WorkOrderKanbanResponse response = workOrderService.getKanbanBoard(
                unitId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obter detalhes completos da ordem de serviço", description = "Retorna dados gerais, veículo, cliente, apontamentos e itens/serviços.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes da ordem de serviço"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById(@PathVariable UUID id) {
        WorkOrderResponse response = workOrderService.getWorkOrderById(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Atualizar informações gerais da ordem de serviço", description = "Atualiza mecânico responsável, box, datas e notas técnicas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordem de serviço atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada"),
            @ApiResponse(responseCode = "422", description = "Ordem de serviço já finalizada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> updateWorkOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkOrderRequest request
    ) {
        WorkOrderResponse response = workOrderService.updateWorkOrder(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Atualizar lista de peças e serviços da ordem de serviço", description = "Substitui e recalcula determinística e integralmente os itens da ordem de serviço.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Itens da ordem de serviço atualizados com sucesso"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada"),
            @ApiResponse(responseCode = "422", description = "Ordem de serviço já finalizada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}/items")
    public ResponseEntity<WorkOrderResponse> updateWorkOrderItems(
            @PathVariable UUID id,
            @Valid @RequestBody List<SaveWorkOrderItemRequest> items
    ) {
        WorkOrderResponse response = workOrderService.updateWorkOrderItems(id, items, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Alterar status operacional da ordem de serviço", description = "Transiciona o status da OS (ex: IN_PROGRESS, WAITING_PARTS, WAITING_CUSTOMER).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status alterado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Transição de status inválida"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_EXECUTE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeWorkOrderStatusRequest request
    ) {
        WorkOrderResponse response = workOrderService.changeStatus(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Finalizar ordem de serviço", description = "Conclui a OS, registra quilometragem final e dispara o evento transacional WorkOrderCompletedEvent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordem de serviço finalizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Transição inválida para finalização"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_CLOSE') or hasRole('Proprietário')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<WorkOrderResponse> completeWorkOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteWorkOrderRequest request
    ) {
        WorkOrderResponse response = workOrderService.completeWorkOrder(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancelar ordem de serviço", description = "Cancela a OS com soft delete e registra motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ordem de serviço cancelada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Ordem de serviço já finalizada"),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_CANCEL') or hasRole('Proprietário')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelWorkOrder(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Cancelado pelo usuário") String reason
    ) {
        workOrderService.cancelWorkOrder(id, reason, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
