package com.gomech.api.core.authorization.api;

import com.gomech.api.core.tenancy.UnitReference;

import java.util.Set;
import java.util.UUID;

/**
 * Who is acting, and within which scope.
 *
 * <p>Assembled from trusted authenticated state only. It is an input to the authorization,
 * entitlement and audit contracts; it carries no decision of its own.
 *
 * @param unit the unit in scope, or null when the request is not scoped to one
 */
public record ActorContext(
    UUID userId,
    UUID tenantId,
    UnitReference unit,
    Set<String> roles,
    Set<String> permissions
) {

    public ActorContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
