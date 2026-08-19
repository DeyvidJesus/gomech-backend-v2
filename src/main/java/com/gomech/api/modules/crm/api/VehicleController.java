package com.gomech.api.modules.crm.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.UpdateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.crm.application.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(name = "CRM Veículos", description = "Gestão de veículos vinculados aos clientes da oficina")
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "Cadastrar novo veículo", description = "Vincula um novo veículo a um cliente existente no mesmo tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Veículo cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
            @ApiResponse(responseCode = "404", description = "Cliente informado não encontrado"),
            @ApiResponse(responseCode = "409", description = "Placa já cadastrada para outro veículo ativo nesta oficina"),
            @ApiResponse(responseCode = "422", description = "Formato de placa inválido (Mercosul ou Tradicional)")
    })
    @PreAuthorize("hasAuthority('CRM_VEHICLE_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = vehicleService.createVehicle(request, null);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + response.id())).body(response);
    }

    @Operation(summary = "Buscar e filtrar veículos de forma paginada", description = "Retorna lista paginada de veículos com suporte a busca livre ou filtros por placa, marca, modelo e cliente.")
    @ApiResponse(responseCode = "200", description = "Página de veículos retornada com sucesso")
    @PreAuthorize("hasAuthority('CRM_VEHICLE_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<PageResponse<VehicleSummaryResponse>> searchVehicles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        if (size > 100) {
            throw new IllegalArgumentException("O tamanho da página (size) não pode exceder 100 elementos");
        }
        Pageable pageable = createPageable(page, size, sort);
        Page<VehicleSummaryResponse> result = vehicleService.searchVehicles(q, licensePlate, brand, model, customerId, pageable, null);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(summary = "Obter detalhes do veículo", description = "Retorna informações detalhadas do veículo pelo identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado ou pertencente a outra oficina")
    })
    @PreAuthorize("hasAuthority('CRM_VEHICLE_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id, null));
    }

    @Operation(summary = "Atualizar dados do veículo", description = "Atualiza placa, marca, modelo, ano, chassi, quilometragem ou transfere para outro cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Veículo atualizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Veículo ou novo cliente não encontrado"),
            @ApiResponse(responseCode = "409", description = "Placa já em uso por outro veículo ativo"),
            @ApiResponse(responseCode = "422", description = "Formato de placa inválido")
    })
    @PreAuthorize("hasAuthority('CRM_VEHICLE_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, request, null));
    }

    @Operation(summary = "Desativar veículo (Soft delete)", description = "Marca o veículo como desativado mantendo o histórico.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Veículo desativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado")
    })
    @PreAuthorize("hasAuthority('CRM_VEHICLE_DELETE') or hasRole('Proprietário')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable UUID id) {
        vehicleService.deleteVehicle(id, null);
        return ResponseEntity.noContent().build();
    }

    private Pageable createPageable(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, Sort.by("createdAt").descending());
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
