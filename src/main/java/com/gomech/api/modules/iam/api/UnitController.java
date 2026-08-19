package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CreateUnitRequest;
import com.gomech.api.modules.iam.api.dto.UnitResponse;
import com.gomech.api.modules.iam.application.UnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "IAM Units & Branches", description = "Gestão de unidades e filiais da oficina")
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UnitController {

    private final UnitService unitService;

    @Operation(summary = "Listar unidades da oficina", description = "Retorna todas as unidades/filiais cadastradas para o tenant autenticado.")
    @ApiResponse(responseCode = "200", description = "Lista de unidades recuperada com sucesso")
    @PreAuthorize("hasAuthority('IAM_UNIT_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<List<UnitResponse>> getUnits() {
        return ResponseEntity.ok(unitService.getUnits(TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Obter detalhes da unidade", description = "Retorna os detalhes de uma unidade específica da oficina.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unidade encontrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Unidade não encontrada ou não pertence à oficina")
    })
    @PreAuthorize("hasAuthority('IAM_UNIT_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<UnitResponse> getUnitById(@PathVariable UUID id) {
        return ResponseEntity.ok(unitService.getUnitById(id, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Criar nova unidade/filial", description = "Cadastra uma nova filial para a oficina.")
    @ApiResponse(responseCode = "201", description = "Unidade criada com sucesso")
    @PreAuthorize("hasAuthority('IAM_UNIT_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<UnitResponse> createUnit(@Valid @RequestBody CreateUnitRequest request) {
        UnitResponse response = unitService.createUnit(request, TenantContextHolder.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
