package com.gomech.api.core.entitlement.api;

import java.util.Map;
import java.util.Set;

/**
 * Snapshot das capacidades, permissões e limites de cota outorgados a um ator ou tenant.
 */
public record EntitlementSnapshot(
        Set<String> permissions,
        Set<String> scopes,
        String planCode,
        Set<String> enabledModules,
        Map<String, Long> quotaLimits
) {
    public EntitlementSnapshot(Set<String> permissions, Set<String> scopes) {
        this(permissions, scopes, "UNKNOWN", Set.of(), Map.of());
    }
}
