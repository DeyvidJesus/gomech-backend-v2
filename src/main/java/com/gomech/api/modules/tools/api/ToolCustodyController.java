package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.ToolCustodyDtos;
import com.gomech.api.modules.tools.application.ToolCustodyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/tools/custody")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Custody", description = "Controle de posse, check-out e check-in de ferramentas")
public class ToolCustodyController {

    private final ToolCustodyService custodyService;

    @PostMapping("/check-out")
    @PreAuthorize("hasAuthority('TOOLS_CUSTODY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Realizar Check-out", description = "Retira a ferramenta para custódia de um mecânico.")
    public ResponseEntity<ToolCustodyDtos.CustodyLogResponse> checkOut(
            @Valid @RequestBody ToolCustodyDtos.CheckOut request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolCustodyDtos.CustodyLogResponse response = custodyService.checkOut(
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAuthority('TOOLS_CUSTODY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Realizar Check-in", description = "Devolve a ferramenta ao armário/gaveta da oficina.")
    public ResponseEntity<ToolCustodyDtos.CustodyLogResponse> checkIn(
            @Valid @RequestBody ToolCustodyDtos.CheckIn request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolCustodyDtos.CustodyLogResponse response = custodyService.checkIn(
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('TOOLS_CUSTODY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Transferência direta de custódia", description = "Transfere a posse direta da ferramenta para outro mecânico.")
    public ResponseEntity<ToolCustodyDtos.CustodyLogResponse> assignTool(
            @Valid @RequestBody ToolCustodyDtos.Assign request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolCustodyDtos.CustodyLogResponse response = custodyService.assignTool(
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{toolId}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Histórico de custódia da ferramenta", description = "Lista toda a linha do tempo de posse da ferramenta.")
    public ResponseEntity<List<ToolCustodyDtos.CustodyLogResponse>> getToolHistory(@PathVariable("toolId") UUID toolId) {
        List<ToolCustodyDtos.CustodyLogResponse> response = custodyService.getToolHistory(
                toolId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Livro de custódia geral", description = "Consulta paginada de todos os registros de custódia.")
    public ResponseEntity<Page<ToolCustodyDtos.CustodyLogResponse>> listAllLogs(
            @RequestParam(name = "toolId", required = false) UUID toolId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ToolCustodyDtos.CustodyLogResponse> response = custodyService.listAllLogs(
                TenantContextHolder.getTenantId(),
                toolId,
                pageable
        );
        return ResponseEntity.ok(response);
    }
}
