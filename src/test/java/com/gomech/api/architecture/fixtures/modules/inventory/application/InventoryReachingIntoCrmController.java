package com.gomech.api.architecture.fixtures.modules.inventory.application;

import com.gomech.api.architecture.fixtures.modules.crm.api.CrmController;

/**
 * Violates modules_must_not_depend_on_another_modules_controllers.
 *
 * <p>Note the target is in crm's PUBLIC api package, so the public-surface rule allows it. Calling
 * another module's HTTP adapter still is not cross-module communication through a contract.
 */
public class InventoryReachingIntoCrmController {

    private CrmController controller;

    public CrmController controller() {
        return controller;
    }
}
