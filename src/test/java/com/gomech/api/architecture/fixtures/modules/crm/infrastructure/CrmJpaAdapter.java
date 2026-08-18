package com.gomech.api.architecture.fixtures.modules.crm.infrastructure;

/** Module-internal persistence adapter. Nothing outside crm may reference it. */
public class CrmJpaAdapter {

    public String load() {
        return "customer";
    }
}
