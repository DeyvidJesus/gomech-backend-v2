package com.gomech.api.modules.inventory.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.inventory.api.dto.CreateReservationRequest;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;
import com.gomech.api.modules.inventory.application.StockReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/inventory/reservations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory - Reservations", description = "Reservas de peças e materiais para ordens de serviço")
public class StockReservationController {

    private final StockReservationService reservationService;

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_WRITE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Criar reserva de estoque", description = "Reserva peças para uma OS sem deduzir saldo físico.")
    public ResponseEntity<StockReservationResponse> createReservation(
        @Valid @RequestBody CreateReservationRequest request,
        @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        StockReservationResponse response = reservationService.createReservation(request, TenantContextHolder.getTenantId(), userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_WRITE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Liberar/Cancelar reserva de estoque", description = "Restaura o saldo disponível desvinculando a reserva.")
    public ResponseEntity<Void> releaseReservation(
        @PathVariable("id") UUID id
    ) {
        reservationService.releaseReservation(id, TenantContextHolder.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-orders/{workOrderId}")
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_READ') or hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar reservas por Ordem de Serviço", description = "Lista todas as peças reservadas para uma OS específica.")
    public ResponseEntity<List<StockReservationResponse>> listReservationsByWorkOrder(
        @PathVariable("workOrderId") UUID workOrderId
    ) {
        List<StockReservationResponse> response = reservationService.listReservationsByWorkOrder(workOrderId, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar reservas ativas por filial", description = "Lista reservas pendentes de consumo em uma filial.")
    public ResponseEntity<List<StockReservationResponse>> listActiveReservationsByUnit(
        @RequestParam("unitId") UUID unitId
    ) {
        List<StockReservationResponse> response = reservationService.listActiveReservationsByUnit(unitId, TenantContextHolder.getTenantId());
        return ResponseEntity.ok(response);
    }
}
