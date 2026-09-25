package com.gomech.api.modules.iam.application;

import com.gomech.api.modules.iam.api.IamContract;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamContractImpl implements IamContract {

    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantContractDto> findTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(t -> new TenantContractDto(
                t.getId(),
                t.getName(),
                t.getTradeName(),
                t.getCnpj(),
                t.getEmail(),
                t.getPhone(),
                t.getGatewayCustomerId(),
                t.getStatus()
        ));
    }

    @Override
    @Transactional
    public void updateGatewayCustomerId(UUID tenantId, String gatewayCustomerId) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.setGatewayCustomerId(gatewayCustomerId);
            tenantRepository.save(tenant);
        });
    }
}
