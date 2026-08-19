package com.gomech.api.core.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID ROOT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            // Em rotas públicas ou threads assíncronas sem tenant.
            return ROOT_TENANT_ID; // Tenant Zero (System / Root) fallback
        }
        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT_TENANT_ID.equals(tenantId);
    }
}
