package com.gomech.api.architecture.fixtures.modules.crm.api;

import com.gomech.api.architecture.fixtures.modules.crm.infrastructure.CrmJpaAdapter;

/** Violates api_must_not_access_infrastructure_directly. */
public class ApiReachingIntoInfrastructure {

    private final CrmJpaAdapter adapter = new CrmJpaAdapter();

    public String load() {
        return adapter.load();
    }
}
