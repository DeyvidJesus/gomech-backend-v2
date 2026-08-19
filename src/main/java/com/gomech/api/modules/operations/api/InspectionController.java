package com.gomech.api.modules.operations.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.api.PageResponse;
import com.gomech.api.modules.operations.api.dto.CompleteInspectionRequest;
import com.gomech.api.modules.operations.api.dto.CreateInspectionRequest;
import com.gomech.api.modules.operations.api.dto.InspectionResponse;
import com.gomech.api.modules.operations.api.dto.InspectionSummaryResponse;
import com.gomech.api.modules.operations.api.dto.SaveInspectionItemRequest;
import com.gomech.api.modules.operations.api.dto.UpdateInspectionRequest;
import com.gomech.api.modules.operations.application.InspectionService;
import com.gomech.api.modules.operations.domain.InspectionStatus;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Operations Inspections", description = "Gestão de inspeções veiculares, checklists de entrada e vistorias técnicas")
@RestController
@RequestMapping("/api/v1/inspections")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InspectionController {

    private final InspectionService inspectionService;

    @Operation(summary = "Criar nova inspeção veicular", description = "Inicia uma vistoria técnica ou checklist de entrada para um veículo e cliente, opcionalmente associado a um agendamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inspeção criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
            @ApiResponse(responseCode = "422", description = "Veículo não pertence ao cliente ou agendamento inconsistente")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<InspectionResponse> createInspection(
            @Valid @RequestBody CreateInspectionRequest request,
            @AuthenticationPrincipal String userId
    ) {
        UUID inspectorId = userId != null ? UUID.fromString(userId) : null;
        InspectionResponse response = inspectionService.createInspection(
                request,
                TenantContextHolder.getTenantId(),
                request.unitId(),
                inspectorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Buscar inspeções com paginação e filtros", description = "Retorna lista paginada de inspeções com filtros opcionais por veículo, cliente, status e unidade.")
    @ApiResponse(responseCode = "200", description = "Lista de inspeções recuperada com sucesso")
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<PageResponse<InspectionSummaryResponse>> searchInspections(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InspectionSummaryResponse> result = inspectionService.searchInspections(
                vehicleId,
                customerId,
                status,
                unitId,
                pageRequest,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(summary = "Obter detalhes completos da inspeção", description = "Retorna todos os dados da inspeção incluindo a lista completa de itens do checklist, status e fotos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inspeção encontrada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Inspeção não encontrada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<InspectionResponse> getInspectionById(@PathVariable UUID id) {
        return ResponseEntity.ok(inspectionService.getInspectionById(id, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Atualizar dados gerais da inspeção", description = "Permite editar nível de combustível, quilometragem e notas gerais enquanto a inspeção estiver em andamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inspeção atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Inspeção não encontrada"),
            @ApiResponse(responseCode = "422", description = "Inspeção já finalizada ou cancelada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}")
    public ResponseEntity<InspectionResponse> updateInspection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInspectionRequest request
    ) {
        return ResponseEntity.ok(inspectionService.updateInspection(id, request, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Atualizar itens e laudos da inspeção", description = "Substitui e atualiza a lista de itens vistoriados, registrando o estado de cada componente (OK, ATENÇÃO, CRÍTICO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Itens da inspeção atualizados com sucesso"),
            @ApiResponse(responseCode = "404", description = "Inspeção não encontrada"),
            @ApiResponse(responseCode = "422", description = "Inspeção já finalizada ou cancelada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}/items")
    public ResponseEntity<InspectionResponse> updateInspectionItems(
            @PathVariable UUID id,
            @Valid @RequestBody List<SaveInspectionItemRequest> items
    ) {
        return ResponseEntity.ok(inspectionService.updateInspectionItems(id, items, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Finalizar inspeção veicular", description = "Conclui a inspeção, congelando os resultados e preparando os dados para a geração de orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inspeção finalizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Inspeção não encontrada"),
            @ApiResponse(responseCode = "422", description = "Inspeção já finalizada ou cancelada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_EXECUTE') or hasRole('Proprietário')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<InspectionResponse> completeInspection(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteInspectionRequest request
    ) {
        return ResponseEntity.ok(inspectionService.completeInspection(id, request, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Cancelar inspeção veicular", description = "Cancela e realiza o soft delete da inspeção.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inspeção cancelada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Inspeção não encontrada"),
            @ApiResponse(responseCode = "422", description = "Inspeção já finalizada ou cancelada")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_INSPECTION_WRITE') or hasRole('Proprietário')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelInspection(@PathVariable UUID id) {
        inspectionService.cancelInspection(id, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
