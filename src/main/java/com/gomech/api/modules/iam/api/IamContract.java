package com.gomech.api.modules.iam.api;

import java.util.Optional;
import java.util.UUID;

public interface IamContract {

    record TenantContractDto(
            UUID id,
            String name,
            String tradeName,
            String cnpj,
            String email,
            String phone,
            String gatewayCustomerId,
            String status
    ) {}

    Optional<TenantContractDto> findTenantById(UUID tenantId);

    void updateGatewayCustomerId(UUID tenantId, String gatewayCustomerId);
}
