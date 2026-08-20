package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.AccountDtos;
import com.gomech.api.modules.finance.application.FinanceAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/accounts")
@RequiredArgsConstructor
@Tag(name = "Finance - Contas", description = "Gestão de contas bancárias, caixas e carteiras")
@SecurityRequirement(name = "bearerAuth")
public class FinanceAccountController {

    private final FinanceAccountService accountService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar contas financeiras")
    public ResponseEntity<List<AccountDtos.Response>> listAccounts(
            @RequestParam(required = false) UUID unitId
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(accountService.listAccounts(tenantId, unitId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Obter detalhes de uma conta financeira")
    public ResponseEntity<AccountDtos.Response> getAccount(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(accountService.getAccount(id, tenantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Cadastrar nova conta financeira")
    public ResponseEntity<AccountDtos.Response> createAccount(@Valid @RequestBody AccountDtos.Create request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Atualizar conta financeira")
    public ResponseEntity<AccountDtos.Response> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountDtos.Update request
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(accountService.updateAccount(id, request, tenantId));
    }
}
