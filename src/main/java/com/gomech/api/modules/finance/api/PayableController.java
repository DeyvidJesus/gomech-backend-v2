package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.PayableDtos;
import com.gomech.api.modules.finance.application.PayableService;
import com.gomech.api.modules.finance.domain.PayableStatus;
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
@RequestMapping("/api/v1/finance/payables")
@RequiredArgsConstructor
@Tag(name = "Finance - Contas a Pagar", description = "Gestão de despesas, fornecedores e liquidação de pagamentos")
@SecurityRequirement(name = "bearerAuth")
public class PayableController {

    private final PayableService payableService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_PAYABLE_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar contas a pagar")
    public ResponseEntity<Page<PayableDtos.Response>> listPayables(
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) PayableStatus status,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(payableService.listPayables(tenantId, unitId, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_PAYABLE_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Obter detalhes de uma conta a pagar")
    public ResponseEntity<PayableDtos.Response> getPayable(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(payableService.getPayable(id, tenantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_PAYABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Criar nova conta a pagar manual")
    public ResponseEntity<PayableDtos.Response> createPayable(@Valid @RequestBody PayableDtos.Create request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(payableService.createPayable(request, tenantId));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('FINANCE_PAYABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Liquidar / Baixar pagamento de conta a pagar")
    public ResponseEntity<PayableDtos.Response> settlePayable(
            @PathVariable UUID id,
            @Valid @RequestBody PayableDtos.Settle request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(payableService.settlePayable(id, request, tenantId, userId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('FINANCE_PAYABLE_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Cancelar conta a pagar pendente")
    public ResponseEntity<PayableDtos.Response> cancelPayable(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(payableService.cancelPayable(id, reason, tenantId));
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
