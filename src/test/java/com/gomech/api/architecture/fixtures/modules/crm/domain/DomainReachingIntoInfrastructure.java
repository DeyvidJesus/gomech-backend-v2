package com.gomech.api.architecture.fixtures.modules.crm.domain;

import com.gomech.api.architecture.fixtures.modules.crm.infrastructure.CrmJpaAdapter;

/** Violates domain_must_not_depend_on_outer_layers. */
public class DomainReachingIntoInfrastructure {

    private final CrmJpaAdapter adapter = new CrmJpaAdapter();

    public String load() {
        return adapter.load();
    }
}
