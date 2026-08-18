package com.gomech.api.architecture.fixtures.modules.crm.infrastructure;

/** Violates module_contracts_must_live_in_api_or_events_packages: a contract hidden in infrastructure. */
public class MisplacedContractEvent {

    public String eventType() {
        return "MisplacedContractEvent";
    }
}
