package com.gomech.api.architecture.fixtures.core.infrastructure;

/** A core implementation detail. Modules must bind to the core abstraction, never to this. */
public class CoreInfrastructureAdapter {

    public String describe() {
        return "core-impl";
    }
}
