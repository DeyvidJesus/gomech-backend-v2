package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CreateRoleRequest;
import com.gomech.api.modules.iam.api.dto.PermissionResponse;
import com.gomech.api.modules.iam.api.dto.RoleResponse;
import com.gomech.api.modules.iam.application.RoleService;
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

@Tag(name = "IAM Roles & Permissions", description = "Gestão de papéis (RBAC) e catálogo de permissões (PBAC) por oficina")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Listar papéis da oficina", description = "Retorna todos os papéis configurados (padrão e customizados) com suas respectivas permissões.")
    @ApiResponse(responseCode = "200", description = "Lista de papéis recuperada com sucesso")
    @PreAuthorize("hasAuthority('IAM_ROLE_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(roleService.getRoles(TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Listar catálogo global de permissões", description = "Retorna todas as permissões disponíveis no sistema por módulo.")
    @ApiResponse(responseCode = "200", description = "Catálogo de permissões recuperado com sucesso")
    @PreAuthorize("hasAuthority('IAM_ROLE_READ') or hasRole('Proprietário')")
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionResponse>> getPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }

    @Operation(summary = "Criar papel customizado", description = "Permite que a oficina crie novos papéis com conjuntos específicos de permissões.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Papel customizado criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nome duplicado ou códigos de permissão inválidos")
    })
    @PreAuthorize("hasAuthority('IAM_ROLE_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createCustomRole(request, TenantContextHolder.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
