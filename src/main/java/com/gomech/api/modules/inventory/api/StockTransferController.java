package com.gomech.api.modules.inventory.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.inventory.api.dto.CreateTransferRequest;
import com.gomech.api.modules.inventory.api.dto.StockTransferResponse;
import com.gomech.api.modules.inventory.application.StockTransferService;
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
@RequestMapping("/api/v1/inventory/transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory - Transfers", description = "Transferências de estoque entre filiais da oficina")
public class StockTransferController {

    private final StockTransferService transferService;

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Solicitar transferência de peças", description = "Inicia uma transferência de peças entre duas filiais.")
    public ResponseEntity<StockTransferResponse> createTransfer(
        @Valid @RequestBody CreateTransferRequest request,
        @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        StockTransferResponse response = transferService.createTransfer(request, TenantContextHolder.getTenantId(), userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Concluir transferência", description = "Deduza o estoque na filial de origem, incrementa no destino e gera movimentações contábeis.")
    public ResponseEntity<StockTransferResponse> completeTransfer(
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        StockTransferResponse response = transferService.completeTransfer(id, TenantContextHolder.getTenantId(), userUuid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Cancelar transferência", description = "Cancela uma transferência pendente.")
    public ResponseEntity<StockTransferResponse> cancelTransfer(
        @PathVariable("id") UUID id,
        @RequestParam(value = "reason", required = false) String reason
    ) {
        StockTransferResponse response = transferService.cancelTransfer(id, reason, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar transferência por ID", description = "Recupera detalhes e itens de uma transferência.")
    public ResponseEntity<StockTransferResponse> getTransferById(
        @PathVariable("id") UUID id
    ) {
        StockTransferResponse response = transferService.getTransferById(id, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar transferências", description = "Lista transferências por filial (origem ou destino).")
    public ResponseEntity<Page<StockTransferResponse>> listTransfers(
        @RequestParam(value = "unitId", required = false) UUID unitId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<StockTransferResponse> response = transferService.listTransfers(
            TenantContextHolder.getTenantId(), unitId, pageable
        );
        return ResponseEntity.ok(response);
    }
}
