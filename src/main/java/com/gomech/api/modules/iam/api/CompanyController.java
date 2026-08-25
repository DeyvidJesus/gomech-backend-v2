package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CompanyProfileResponse;
import com.gomech.api.modules.iam.api.dto.UpdateCompanyProfileRequest;
import com.gomech.api.modules.iam.application.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "IAM Company Profile", description = "Gestão do perfil cadastral e logomarca da empresa")
@RestController
@RequestMapping("/api/v1/company/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "Obter dados da empresa", description = "Retorna os dados cadastrais da empresa/oficina autenticada.")
    @ApiResponse(responseCode = "200", description = "Dados da empresa recuperados com sucesso")
    @PreAuthorize("hasAuthority('IAM_COMPANY_READ') or hasRole('Proprietário') or hasRole('Gerente')")
    @GetMapping
    public ResponseEntity<CompanyProfileResponse> getCompanyProfile() {
        return ResponseEntity.ok(companyService.getCompanyProfile(TenantContextHolder.getTenantId()));
    }

    @Operation(summary = "Atualizar perfil da empresa", description = "Atualiza nome fantasia, e-mail, telefone, logo e endereço. O CNPJ é imutável.")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso")
    @PreAuthorize("hasAuthority('IAM_COMPANY_WRITE') or hasRole('Proprietário')")
    @PutMapping
    public ResponseEntity<CompanyProfileResponse> updateCompanyProfile(@Valid @RequestBody UpdateCompanyProfileRequest request) {
        return ResponseEntity.ok(companyService.updateCompanyProfile(request, TenantContextHolder.getTenantId()));
    }
}
