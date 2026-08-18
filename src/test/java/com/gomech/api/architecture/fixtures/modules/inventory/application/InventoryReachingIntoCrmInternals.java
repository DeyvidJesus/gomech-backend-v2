package com.gomech.api.architecture.fixtures.modules.inventory.application;

import com.gomech.api.architecture.fixtures.modules.crm.services.CrmInternalService;

/** Violates cross_module_access_must_target_public_contracts_only. */
public class InventoryReachingIntoCrmInternals {

    private final CrmInternalService service = new CrmInternalService();

    public String describe() {
        return service.describe();
    }
}
