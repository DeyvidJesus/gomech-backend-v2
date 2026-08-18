package com.gomech.api.architecture.fixtures.modules.inventory.application;

import com.gomech.api.core.events.SpringDomainEventBus;

/**
 * Violates modules_must_use_the_event_bus_contract_not_its_implementation.
 *
 * <p>Publishing is legitimate for a module; binding to the Spring implementation of the bus rather
 * than to the {@code DomainEventBus} contract is not.
 */
public class InventoryReachingIntoEventBusImplementation {

    private SpringDomainEventBus bus;

    public SpringDomainEventBus bus() {
        return bus;
    }
}
