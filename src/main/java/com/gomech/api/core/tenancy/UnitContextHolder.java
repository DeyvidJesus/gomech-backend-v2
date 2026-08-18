package com.gomech.api.core.tenancy;

import java.util.Optional;

/**
 * Holds the unit the current request is scoped to, mirroring {@link TenantContextHolder}.
 *
 * <p>The unit is only ever established from trusted authenticated state. There is deliberately no
 * caller-provided channel: unlike the tenant, no public endpoint needs to select a unit before
 * authenticating, and deciding which units an actor may act on is authorization, which is out of
 * scope here.
 *
 * <p>Absent unit scope is a normal state, so reads return an {@link Optional} rather than null.
 */
public final class UnitContextHolder {

    private static final ThreadLocal<UnitReference> UNIT_CONTEXT = new ThreadLocal<>();

    private UnitContextHolder() {
    }

    /** Establishes the unit from verified authentication state. */
    public static void setUnit(UnitReference unit) {
        if (unit == null) {
            UNIT_CONTEXT.remove();
            return;
        }
        UNIT_CONTEXT.set(unit);
    }

    /** The unit in scope, or empty when the request carries none. */
    public static Optional<UnitReference> getUnit() {
        return Optional.ofNullable(UNIT_CONTEXT.get());
    }

    public static void clear() {
        UNIT_CONTEXT.remove();
    }
}
