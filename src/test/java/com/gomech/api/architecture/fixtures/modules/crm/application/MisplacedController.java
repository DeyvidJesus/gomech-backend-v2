package com.gomech.api.architecture.fixtures.modules.crm.application;

/** Violates controllers_must_reside_in_the_api_layer: an HTTP adapter sitting in application. */
public class MisplacedController {

    public String handle() {
        return "ok";
    }
}
