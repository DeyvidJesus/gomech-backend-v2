package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.CreateToolRequest;
import com.gomech.api.modules.tools.api.dto.ToolResponse;
import com.gomech.api.modules.tools.api.dto.UpdateToolRequest;
import com.gomech.api.modules.tools.application.ToolService;
import com.gomech.api.modules.tools.domain.ToolStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Assets", description = "Gestão de ferramentas, equipamentos e ativos reutilizáveis")
public class ToolController {

    private final ToolService toolService;

    @PostMapping
    @PreAuthorize("hasAuthority('TOOLS_TOOL_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Cadastrar ferramenta/equipamento", description = "Cadastra um novo ativo com tag de patrimônio na oficina.")
    public ResponseEntity<ToolResponse> createTool(@Valid @RequestBody CreateToolRequest request) {
        ToolResponse response = toolService.createTool(request, TenantContextHolder.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Atualizar ferramenta", description = "Atualiza informações cadastrais de uma ferramenta.")
    public ResponseEntity<ToolResponse> updateTool(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateToolRequest request
    ) {
        ToolResponse response = toolService.updateTool(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Buscar ferramenta por ID", description = "Retorna detalhes completos de uma ferramenta.")
    public ResponseEntity<ToolResponse> getTool(@PathVariable("id") UUID id) {
        ToolResponse response = toolService.getTool(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar ferramentas", description = "Lista ferramentas com filtros por filial, status, categoria e busca.")
    public ResponseEntity<Page<ToolResponse>> listTools(
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "status", required = false) ToolStatus status,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ToolResponse> response = toolService.listTools(
                TenantContextHolder.getTenantId(),
                unitId,
                status,
                categoryId,
                search,
                pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar ferramentas disponíveis", description = "Lista ferramentas disponíveis para check-out na filial informada.")
    public ResponseEntity<List<ToolResponse>> listAvailableTools(@RequestParam("unitId") UUID unitId) {
        List<ToolResponse> response = toolService.listAvailableTools(TenantContextHolder.getTenantId(), unitId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_DELETE') or hasRole('Proprietário')")
    @Operation(summary = "Desativar ferramenta", description = "Realiza a exclusão lógica (soft delete) da ferramenta.")
    public ResponseEntity<Void> deleteTool(@PathVariable("id") UUID id) {
        toolService.deleteTool(id, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
