package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AssignUserRoleRequest;
import com.gomech.api.modules.iam.api.dto.CreateUserRequest;
import com.gomech.api.modules.iam.api.dto.UserResponse;
import com.gomech.api.modules.iam.application.UserService;
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

@Tag(name = "IAM Users", description = "Gestão de usuários da oficina e atribuições de papéis por unidade")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Listar usuários da oficina", description = "Retorna todos os usuários vinculados à oficina autenticada.")
    @ApiResponse(responseCode = "200", description = "Lista de usuários recuperada com sucesso")
    @PreAuthorize("hasAuthority('IAM_USER_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(userService.getUsers(TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Obter detalhes do usuário", description = "Retorna os dados cadastrais e lista de papéis/unidades do usuário.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Usuário não encontrado ou de outra oficina")
    })
    @PreAuthorize("hasAuthority('IAM_USER_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id, TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Cadastrar novo usuário", description = "Cadastra um novo membro da equipe da oficina com seus papéis iniciais.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "E-mail já existente ou papel/unidade inválidos")
    })
    @PreAuthorize("hasAuthority('IAM_USER_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @Operation(summary = "Atribuir papel ao usuário", description = "Vincula um papel existente a um usuário para uma unidade específica ou tenant-wide.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Papel atribuído com sucesso"),
            @ApiResponse(responseCode = "400", description = "Papel já atribuído ou entidade inválida")
    })
    @PreAuthorize("hasAuthority('IAM_USER_WRITE') or hasRole('Proprietário')")
    @PostMapping("/{id}/roles")
    public ResponseEntity<UserResponse> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AssignUserRoleRequest request
    ) {
        return ResponseEntity.ok(userService.assignRole(id, request, TenantContextHolder.getTenantId()));
    }
}
