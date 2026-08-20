package com.gomech.api.modules.tools.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.tools.api.dto.ToolMaintenanceDtos;
import com.gomech.api.modules.tools.application.ToolMaintenanceService;
import com.gomech.api.modules.tools.domain.MaintenanceStatus;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools/maintenances")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tools - Maintenances", description = "Manutenção preventiva, corretiva e calibração de ferramentas")
public class ToolMaintenanceController {

    private final ToolMaintenanceService maintenanceService;

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('TOOLS_MAINTENANCE_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Agendar manutenção/calibração", description = "Agenda manutenção preventiva ou calibração periódica.")
    public ResponseEntity<ToolMaintenanceDtos.Response> scheduleMaintenance(
            @Valid @RequestBody ToolMaintenanceDtos.Schedule request
    ) {
        ToolMaintenanceDtos.Response response = maintenanceService.scheduleMaintenance(
                request,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TOOLS_MAINTENANCE_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Concluir manutenção/calibração", description = "Registra a conclusão do serviço com laudos, custos e data da próxima aferição.")
    public ResponseEntity<ToolMaintenanceDtos.Response> completeMaintenance(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ToolMaintenanceDtos.Complete request,
            @AuthenticationPrincipal String userId
    ) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        ToolMaintenanceDtos.Response response = maintenanceService.completeMaintenance(
                id,
                request,
                TenantContextHolder.getTenantId(),
                userUuid
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TOOLS_MAINTENANCE_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar manutenções", description = "Lista manutenções com filtros por filial, status e ferramenta.")
    public ResponseEntity<Page<ToolMaintenanceDtos.Response>> listMaintenances(
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "status", required = false) MaintenanceStatus status,
            @RequestParam(name = "toolId", required = false) UUID toolId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ToolMaintenanceDtos.Response> response = maintenanceService.listMaintenances(
                TenantContextHolder.getTenantId(),
                unitId,
                status,
                toolId,
                pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tool/{toolId}")
    @PreAuthorize("hasAuthority('TOOLS_MAINTENANCE_READ') or hasRole('Proprietário')")
    @Operation(summary = "Histórico de manutenções da ferramenta", description = "Lista todo o histórico de manutenções e calibrações de uma ferramenta.")
    public ResponseEntity<List<ToolMaintenanceDtos.Response>> getMaintenanceHistoryForTool(
            @PathVariable("toolId") UUID toolId
    ) {
        List<ToolMaintenanceDtos.Response> response = maintenanceService.getMaintenanceHistoryForTool(
                toolId,
                TenantContextHolder.getTenantId()
        );
        return ResponseEntity.ok(response);
    }
}
