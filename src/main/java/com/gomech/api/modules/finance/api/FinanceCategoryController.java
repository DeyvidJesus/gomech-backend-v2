package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.CategoryDtos;
import com.gomech.api.modules.finance.application.FinanceCategoryService;
import com.gomech.api.modules.finance.domain.TransactionType;
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
@RequestMapping("/api/v1/finance/categories")
@RequiredArgsConstructor
@Tag(name = "Finance - Categorias / Plano de Contas", description = "Classificação de receitas e despesas para DRE")
@SecurityRequirement(name = "bearerAuth")
public class FinanceCategoryController {

    private final FinanceCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar categorias do plano de contas")
    public ResponseEntity<List<CategoryDtos.Response>> listCategories(
            @RequestParam(required = false) TransactionType type
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(categoryService.listCategories(tenantId, type));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Criar nova categoria financeira")
    public ResponseEntity<CategoryDtos.Response> createCategory(@Valid @RequestBody CategoryDtos.Create request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_ACCOUNT_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Atualizar categoria financeira")
    public ResponseEntity<CategoryDtos.Response> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDtos.Update request
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(categoryService.updateCategory(id, request, tenantId));
    }
}
