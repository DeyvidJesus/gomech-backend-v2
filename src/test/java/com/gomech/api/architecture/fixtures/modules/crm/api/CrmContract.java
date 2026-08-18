package com.gomech.api.architecture.fixtures.modules.crm.api;

import com.gomech.api.architecture.fixtures.modules.inventory.api.InventoryContract;

/**
 * Published contract of the fixture "crm" module. It depends on another module's api package,
 * which is allowed, and closes a module cycle together with {@link InventoryContract}, which is not.
 */
public class CrmContract {

    private InventoryContract inventoryContract;

    public InventoryContract inventoryContract() {
        return inventoryContract;
    }
}
