package com.gomech.api.architecture.fixtures.modules.inventory.application;

import com.gomech.api.architecture.fixtures.modules.crm.repositories.CrmCustomerRepository;

/**
 * Violates cross_module_access_must_not_target_persistence and
 * repositories_must_not_be_imported_outside_their_module.
 */
public class InventoryReachingIntoCrmRepository {

    private CrmCustomerRepository customerRepository;

    public long countCustomers() {
        return customerRepository.count();
    }
}
