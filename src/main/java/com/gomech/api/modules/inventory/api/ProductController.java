package com.gomech.api.modules.inventory.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.inventory.api.dto.CreateProductRequest;
import com.gomech.api.modules.inventory.api.dto.ProductResponse;
import com.gomech.api.modules.inventory.api.dto.UpdateProductRequest;
import com.gomech.api.modules.inventory.application.ProductService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory - Products", description = "Catálogo de produtos, peças e consumíveis de estoque")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_PRODUCT_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Criar produto no catálogo", description = "Cadastra um novo produto ou peça consumível no estoque.")
    public ResponseEntity<ProductResponse> createProduct(
        @Valid @RequestBody CreateProductRequest request,
        @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ProductResponse response = productService.createProduct(request, TenantContextHolder.getTenantId(), userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_PRODUCT_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Atualizar produto", description = "Atualiza dados cadastrais, preços e limites de estoque do produto.")
    public ResponseEntity<ProductResponse> updateProduct(
        @PathVariable("id") UUID id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(id, request, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_PRODUCT_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar produto por ID", description = "Recupera detalhes cadastrais de um produto específico.")
    public ResponseEntity<ProductResponse> getProductById(
        @PathVariable("id") UUID id
    ) {
        ProductResponse response = productService.getProductById(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_PRODUCT_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar produtos com filtros", description = "Lista produtos do tenant com busca textual por nome, SKU, código de barras e categoria.")
    public ResponseEntity<Page<ProductResponse>> listProducts(
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "active", required = false) Boolean active,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductResponse> response = productService.listProducts(
            TenantContextHolder.getTenantId(), search, category, active, pageable
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_PRODUCT_DELETE') or hasRole('Proprietário')")
    @Operation(summary = "Remover produto (soft delete)", description = "Desativa e marca o produto como deletado.")
    public ResponseEntity<Void> deleteProduct(
        @PathVariable("id") UUID id
    ) {
        productService.deleteProduct(id, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
