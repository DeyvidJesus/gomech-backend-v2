package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.TransactionDtos;
import com.gomech.api.modules.finance.application.TransactionService;
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
@RequestMapping("/api/v1/finance/transactions")
@RequiredArgsConstructor
@Tag(name = "Finance - Extrato & Transações", description = "Extrato financeiro unificado e conciliação bancária")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_TRANSACTION_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar transações financeiras / extrato")
    public ResponseEntity<Page<TransactionDtos.Response>> listTransactions(
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID accountId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(transactionService.listTransactions(tenantId, unitId, accountId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_TRANSACTION_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Lançar transação financeira manual (Crédito ou Débito)")
    public ResponseEntity<TransactionDtos.Response> createTransaction(
            @Valid @RequestBody TransactionDtos.Create request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.createManualTransaction(request, tenantId, userId));
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
