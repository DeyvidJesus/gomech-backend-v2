package com.gomech.api.architecture.fixtures.modules.crm.repositories;

/** Module-owned repository. Importing it from another module is a persistence-ownership violation. */
public interface CrmCustomerRepository {

    long count();
}
