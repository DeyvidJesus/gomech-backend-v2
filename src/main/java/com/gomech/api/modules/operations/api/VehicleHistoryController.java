package com.gomech.api.modules.operations.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryExportResponse;
import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryResponse;
import com.gomech.api.modules.operations.application.VehicleHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Operations Vehicle History", description = "Consulta agregada de histórico de manutenções, peças trocadas, serviços e dossiê exportável do veículo")
@RestController
@RequestMapping("/api/v1/operations/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VehicleHistoryController {

    private final VehicleHistoryService vehicleHistoryService;

    @Operation(summary = "Consultar histórico completo de manutenções do veículo", description = "Agrega todas as ordens de serviço concluídas, peças substituídas, vistorias e métricas de investimento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico do veículo retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado ou não pertence a esta oficina")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_READ') or hasAuthority('CRM_VEHICLE_READ') or hasRole('Proprietário')")
    @GetMapping("/{vehicleId}/history")
    public ResponseEntity<VehicleServiceHistoryResponse> getVehicleServiceHistory(
            @PathVariable UUID vehicleId
    ) {
        VehicleServiceHistoryResponse response = vehicleHistoryService.getVehicleServiceHistory(
                vehicleId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Gerar dossiê exportável de histórico do veículo", description = "Gera o relatório de histórico com código de verificação de autenticidade e termos de garantia para compartilhamento com o cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dossiê exportável gerado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado ou não pertence a esta oficina")
    })
    @PreAuthorize("hasAuthority('OPERATIONS_ORDER_READ') or hasAuthority('CRM_VEHICLE_READ') or hasRole('Proprietário')")
    @GetMapping("/{vehicleId}/history/export")
    public ResponseEntity<VehicleServiceHistoryExportResponse> getVehicleServiceHistoryExport(
            @PathVariable UUID vehicleId
    ) {
        VehicleServiceHistoryExportResponse response = vehicleHistoryService.getVehicleServiceHistoryExport(
                vehicleId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }
}
