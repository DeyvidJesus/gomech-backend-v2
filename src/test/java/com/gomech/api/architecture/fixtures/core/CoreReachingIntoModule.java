package com.gomech.api.architecture.fixtures.core;

import com.gomech.api.architecture.fixtures.modules.crm.api.CrmContract;

/** Violates core_must_not_depend_on_business_modules. */
public class CoreReachingIntoModule {

    private CrmContract contract;

    public CrmContract contract() {
        return contract;
    }
}
