package com.gomech.api.modules.inventory.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.inventory.api.dto.InventoryMovementResponse;
import com.gomech.api.modules.inventory.application.InventoryMovementService;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.MovementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/movements")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory - Movements", description = "Livro-razão imutável de movimentações de estoque (Append-only Ledger)")
public class InventoryMovementController {

    private final InventoryMovementService movementService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_MOVEMENT_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar livro-razão de movimentações", description = "Consulta o histórico imutável e auditável de movimentações de estoque.")
    public ResponseEntity<Page<InventoryMovementResponse>> listMovements(
        @RequestParam(value = "unitId", required = false) UUID unitId,
        @RequestParam(value = "productId", required = false) UUID productId,
        @RequestParam(value = "type", required = false) MovementType type,
        @RequestParam(value = "reason", required = false) MovementReason reason,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<InventoryMovementResponse> response = movementService.listMovements(
            TenantContextHolder.getTenantId(), unitId, productId, type, reason, pageable
        );
        return ResponseEntity.ok(response);
    }
}
