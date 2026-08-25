package com.gomech.api.modules.iam.application;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CompanyProfileResponse;
import com.gomech.api.modules.iam.api.dto.UpdateCompanyProfileRequest;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Tenant;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public CompanyProfileResponse getCompanyProfile(UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        Tenant tenant = tenantRepository.findById(effectiveTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa/Tenant não encontrado: " + effectiveTenantId));

        return toResponse(tenant);
    }

    @Transactional
    public CompanyProfileResponse updateCompanyProfile(UpdateCompanyProfileRequest request, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        Tenant tenant = tenantRepository.findById(effectiveTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa/Tenant não encontrado: " + effectiveTenantId));

        // CNPJ é estritamente IMUTÁVEL
        tenant.setName(request.name());
        tenant.setTradeName(request.tradeName());
        tenant.setEmail(request.email());
        tenant.setPhone(request.phone());
        tenant.setLogoUrl(request.logoUrl());
        tenant.setAddress(request.address());
        tenant.setUpdatedAt(OffsetDateTime.now());

        Tenant updated = tenantRepository.save(tenant);
        log.info("Perfil da empresa {} atualizado com sucesso (CNPJ mantido: {})", updated.getId(), updated.getCnpj());
        return toResponse(updated);
    }

    private CompanyProfileResponse toResponse(Tenant tenant) {
        return new CompanyProfileResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getTradeName() != null ? tenant.getTradeName() : tenant.getName(),
                tenant.getCnpj(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getLogoUrl(),
                tenant.getAddress(),
                tenant.getStatus()
        );
    }
}
