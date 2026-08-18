package com.gomech.api.architecture.fixtures.modules.inventory.application;

import com.gomech.api.architecture.fixtures.core.infrastructure.CoreInfrastructureAdapter;

/** Violates modules_must_not_depend_on_core_infrastructure. */
public class InventoryReachingIntoCoreInfrastructure {

    private CoreInfrastructureAdapter adapter;

    public CoreInfrastructureAdapter adapter() {
        return adapter;
    }
}
