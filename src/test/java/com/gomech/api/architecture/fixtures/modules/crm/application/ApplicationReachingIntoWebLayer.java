package com.gomech.api.architecture.fixtures.modules.crm.application;

import org.springframework.web.context.request.WebRequest;

/**
 * Violates application_must_not_depend_on_api_controllers.
 *
 * <p>The violation is expressed through a Spring web type rather than a {@code @RestController}
 * annotated fixture on purpose: these fixtures live under {@code com.gomech.api}, so any Spring
 * stereotype on them would be picked up by component scanning in {@code GoMechV2ApiApplicationIT}.
 * Depending on an api DTO would NOT be a violation, since ADR-002 places request/response DTOs in
 * the api package by design.
 */
public class ApplicationReachingIntoWebLayer {

    private WebRequest webRequest;

    public WebRequest webRequest() {
        return webRequest;
    }
}
