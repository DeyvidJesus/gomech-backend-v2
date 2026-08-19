package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.application.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "IAM Onboarding", description = "Endpoints para registro de oficinas e onboarding de novos usuários")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "Cadastro de nova oficina (Onboarding)", description = "Cria uma nova organização (Tenant), unidade física matriz, perfil de proprietário e usuário administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Oficina e usuário registrados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de requisição inválidos ou e-mail já cadastrado")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterWorkshopRequest request) {
        UUID newTenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(newTenantId);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(onboardingService.register(request, newTenantId));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
