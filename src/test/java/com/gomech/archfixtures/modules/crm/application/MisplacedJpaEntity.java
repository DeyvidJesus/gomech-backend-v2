package com.gomech.archfixtures.modules.crm.application;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

/**
 * Violates jpa_entities_must_reside_in_the_infrastructure_layer: a JPA entity sitting in the
 * application layer, where the package name is a perfectly valid layer but the responsibility is
 * persistence.
 *
 * <p>It is placed in the {@code application} layer rather than {@code domain} so it breaks exactly
 * one rule: in {@code domain} it would also trip
 * {@code domain_must_not_depend_on_frameworks}, which would make this a weaker proof of the rule
 * under test.
 */
@Entity
public class MisplacedJpaEntity {

    @Id
    private UUID id;

    public UUID id() {
        return id;
    }
}
