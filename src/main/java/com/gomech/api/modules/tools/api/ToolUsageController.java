package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.ToolUsageDtos;
import com.gomech.api.modules.tools.application.ToolUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools/usages")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Usages", description = "Vínculo de uso de ferramentas em Ordens de Serviço")
public class ToolUsageController {

    private final ToolUsageService toolUsageService;

    @PostMapping
    @PreAuthorize("hasAuthority('TOOLS_CUSTODY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Registrar uso em OS", description = "Vincula a ferramenta a uma Ordem de Serviço ativa.")
    public ResponseEntity<ToolUsageDtos.UsageResponse> recordUsage(
            @Valid @RequestBody ToolUsageDtos.RecordUsage request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolUsageDtos.UsageResponse response = toolUsageService.recordUsage(
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAuthority('TOOLS_CUSTODY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Finalizar uso de ferramenta", description = "Devolve a ferramenta e encerra o vínculo com a OS.")
    public ResponseEntity<ToolUsageDtos.UsageResponse> finishUsage(
            @PathVariable("id") UUID id,
            @RequestParam(name = "notes", required = false) String notes,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolUsageDtos.UsageResponse response = toolUsageService.finishUsage(
                id,
                notes,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tool/{toolId}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Histórico de uso da ferramenta em OS", description = "Lista todas as OS em que a ferramenta foi utilizada.")
    public ResponseEntity<List<ToolUsageDtos.UsageResponse>> listUsagesByTool(@PathVariable("toolId") UUID toolId) {
        List<ToolUsageDtos.UsageResponse> response = toolUsageService.listUsagesByTool(
                toolId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/work-order/{workOrderId}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Ferramentas utilizadas na OS", description = "Lista todas as ferramentas vinculadas a uma Ordem de Serviço.")
    public ResponseEntity<List<ToolUsageDtos.UsageResponse>> listUsagesByWorkOrder(@PathVariable("workOrderId") UUID workOrderId) {
        List<ToolUsageDtos.UsageResponse> response = toolUsageService.listUsagesByWorkOrder(
                workOrderId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }
}
