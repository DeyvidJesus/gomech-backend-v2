package com.gomech.api.core.tenancy;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies the unit a request is scoped to.
 *
 * <p>This is deliberately a bare identifier and nothing else. Core must be able to carry unit scope
 * through a request without knowing what a unit <em>is</em>: the business concept, its attributes and
 * its rules belong to the owning module, and core must not depend on that module's JPA entity
 * (ADR-002, {@code core_must_not_depend_on_business_modules}).
 *
 * <p>Modules resolve this reference into their own representation when they need more than the id.
 */
public record UnitReference(UUID id) {

    public UnitReference {
        Objects.requireNonNull(id, "unit id must not be null");
    }

    public static UnitReference of(UUID id) {
        return new UnitReference(id);
    }
}
