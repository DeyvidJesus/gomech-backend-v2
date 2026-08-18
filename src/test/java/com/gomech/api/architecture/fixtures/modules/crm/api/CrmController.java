package com.gomech.api.architecture.fixtures.modules.crm.api;

/**
 * A correctly placed HTTP adapter: it lives in the api layer, so it satisfies
 * controllers_must_reside_in_the_api_layer. It is still not a cross-module entry point.
 *
 * <p>Detected as a controller by name rather than by a Spring stereotype, because these fixtures sit
 * under {@code com.gomech.api} and an annotated one would be component scanned by the integration test.
 */
public class CrmController {

    public String handle() {
        return "ok";
    }
}
