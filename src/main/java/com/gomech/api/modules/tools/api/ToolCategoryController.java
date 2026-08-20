package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.ToolCategoryRequest;
import com.gomech.api.modules.tools.api.dto.ToolCategoryResponse;
import com.gomech.api.modules.tools.application.ToolCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Categories", description = "Categorias de ferramentas e equipamentos")
public class ToolCategoryController {

    private final ToolCategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasAuthority('TOOLS_CATEGORY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Criar categoria de ferramenta", description = "Cadastra uma categoria de ferramenta com parâmetros de calibração.")
    public ResponseEntity<ToolCategoryResponse> createCategory(@Valid @RequestBody ToolCategoryRequest.Create request) {
        ToolCategoryResponse response = categoryService.createCategory(request, TenantContextHolder.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_CATEGORY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Atualizar categoria", description = "Atualiza uma categoria de ferramenta existente.")
    public ResponseEntity<ToolCategoryResponse> updateCategory(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ToolCategoryRequest.Update request
    ) {
        ToolCategoryResponse response = categoryService.updateCategory(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar categorias", description = "Lista todas as categorias de ferramentas cadastradas.")
    public ResponseEntity<List<ToolCategoryResponse>> listCategories() {
        List<ToolCategoryResponse> response = categoryService.listCategories(TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_TOOL_READ') or hasRole('Proprietário')")
    @Operation(summary = "Buscar categoria por ID", description = "Retorna detalhes de uma categoria de ferramentas.")
    public ResponseEntity<ToolCategoryResponse> getCategory(@PathVariable("id") UUID id) {
        ToolCategoryResponse response = categoryService.getCategory(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TOOLS_CATEGORY_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Excluir categoria", description = "Remove uma categoria de ferramenta.")
    public ResponseEntity<Void> deleteCategory(@PathVariable("id") UUID id) {
        categoryService.deleteCategory(id, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
