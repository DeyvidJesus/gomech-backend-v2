package com.gomech.api.modules.inventory.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.inventory.api.dto.AdjustStockRequest;
import com.gomech.api.modules.inventory.api.dto.LowStockProductResponse;
import com.gomech.api.modules.inventory.api.dto.UnitStockResponse;
import com.gomech.api.modules.inventory.application.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/inventory/stocks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory - Stocks", description = "Saldos físicos, disponíveis e alertas de estoque por filial")
public class StockController {

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar saldos de estoque por unidade", description = "Recupera os saldos físicos e reservados de todos os produtos de uma filial.")
    public ResponseEntity<List<UnitStockResponse>> getUnitStocks(
        @RequestParam("unitId") UUID unitId
    ) {
        List<UnitStockResponse> response = stockService.getUnitStocks(TenantContextHolder.getTenantId(), unitId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar saldo de um produto na unidade", description = "Recupera o saldo físico e disponível de um produto específico em uma filial.")
    public ResponseEntity<UnitStockResponse> getStockForProduct(
        @PathVariable("productId") UUID productId,
        @RequestParam("unitId") UUID unitId
    ) {
        UnitStockResponse response = stockService.getStockForProduct(TenantContextHolder.getTenantId(), unitId, productId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_ADJUST') or hasAuthority('INVENTORY_STOCK_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Ajuste manual de inventário", description = "Ajusta o saldo físico de um produto e gera registro imutável no livro-razão de movimentações.")
    public ResponseEntity<UnitStockResponse> adjustStock(
        @Valid @RequestBody AdjustStockRequest request,
        @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        UnitStockResponse response = stockService.adjustStock(request, TenantContextHolder.getTenantId(), userUuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_READ') or hasRole('Proprietário')")
    @Operation(summary = "Alertas de estoque baixo", description = "Lista produtos cujo saldo físico está abaixo ou igual ao limite de estoque mínimo.")
    public ResponseEntity<List<LowStockProductResponse>> getLowStockAlerts(
        @RequestParam("unitId") UUID unitId
    ) {
        List<LowStockProductResponse> response = stockService.getLowStockAlerts(TenantContextHolder.getTenantId(), unitId);
        return ResponseEntity.ok(response);
    }
}
