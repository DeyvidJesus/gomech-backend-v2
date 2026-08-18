package com.gomech.api.core.entitlement.api;

import java.util.Set;

public record EntitlementSnapshot(
    Set<String> permissions,
    Set<String> scopes
) {
}
