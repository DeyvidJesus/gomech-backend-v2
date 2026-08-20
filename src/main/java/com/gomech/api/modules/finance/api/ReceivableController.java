package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.ReceivableDtos;
import com.gomech.api.modules.finance.application.ReceivableService;
import com.gomech.api.modules.finance.domain.ReceivableStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/receivables")
@RequiredArgsConstructor
@Tag(name = "Finance - Contas a Receber", description = "Gestão de títulos, cobranças e baixas de pagamentos")
@SecurityRequirement(name = "bearerAuth")
public class ReceivableController {

    private final ReceivableService receivableService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_RECEIVABLE_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar títulos a receber")
    public ResponseEntity<Page<ReceivableDtos.Response>> listReceivables(
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) ReceivableStatus status,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(receivableService.listReceivables(tenantId, unitId, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_RECEIVABLE_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Obter detalhes de uma conta a receber")
    public ResponseEntity<ReceivableDtos.Response> getReceivable(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(receivableService.getReceivable(id, tenantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_RECEIVABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Criar nova conta a receber manual")
    public ResponseEntity<ReceivableDtos.Response> createReceivable(@Valid @RequestBody ReceivableDtos.Create request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(receivableService.createReceivable(request, tenantId));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('FINANCE_RECEIVABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Liquidar / Baixar pagamento de conta a receber")
    public ResponseEntity<ReceivableDtos.Response> settleReceivable(
            @PathVariable UUID id,
            @Valid @RequestBody ReceivableDtos.Settle request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(receivableService.settleReceivable(id, request, tenantId, userId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('FINANCE_RECEIVABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Cancelar conta a receber pendente")
    public ResponseEntity<ReceivableDtos.Response> cancelReceivable(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(receivableService.cancelReceivable(id, reason, tenantId));
    }

    private UUID extractUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
