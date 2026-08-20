package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.ToolTransferDtos;
import com.gomech.api.modules.tools.application.ToolTransferService;
import com.gomech.api.modules.tools.domain.ToolTransferStatus;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools/transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Transfers", description = "Transferências de ferramentas e equipamentos entre filiais")
public class ToolTransferController {

    private final ToolTransferService transferService;

    @PostMapping
    @PreAuthorize("hasAuthority('TOOLS_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Solicitar transferência entre filiais", description = "Inicia a remessa de uma ferramenta para outra filial.")
    public ResponseEntity<ToolTransferDtos.Response> createTransfer(
            @Valid @RequestBody ToolTransferDtos.Create request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolTransferDtos.Response response = transferService.createTransfer(
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TOOLS_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Concluir recebimento de ferramenta", description = "Confirma o recebimento e atualiza a filial da ferramenta.")
    public ResponseEntity<ToolTransferDtos.Response> completeTransfer(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolTransferDtos.Response response = transferService.completeTransfer(
                id,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TOOLS_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Cancelar transferência", description = "Cancela a transferência e restaura a disponibilidade na filial de origem.")
    public ResponseEntity<ToolTransferDtos.Response> cancelTransfer(
            @PathVariable("id") UUID id,
            @RequestParam(name = "reason", required = false) String reason
    ) {
        ToolTransferDtos.Response response = transferService.cancelTransfer(
                id,
                reason,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TOOLS_TRANSFER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar transferências", description = "Lista transferências de ferramentas com filtros por filial e status.")
    public ResponseEntity<Page<ToolTransferDtos.Response>> listTransfers(
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "status", required = false) ToolTransferStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ToolTransferDtos.Response> response = transferService.listTransfers(
                TenantContextHolder.getTenantId(),
                unitId,
                status,
                pageable
        );
        return ResponseEntity.ok(response);
    }
}
