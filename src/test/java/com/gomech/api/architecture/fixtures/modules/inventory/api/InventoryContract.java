package com.gomech.api.architecture.fixtures.modules.inventory.api;

import com.gomech.api.architecture.fixtures.modules.crm.api.CrmContract;

/** Closes the module cycle with {@code CrmContract}: violates modules_must_be_free_of_cycles. */
public class InventoryContract {

    private CrmContract crmContract;

    public CrmContract crmContract() {
        return crmContract;
    }
}
