package com.gomech.api.architecture.fixtures.modules.crm.domain;

import org.springframework.data.domain.Sort;

/** Violates domain_must_not_depend_on_frameworks. */
public class DomainReachingIntoFramework {

    private Sort sort;

    public Sort sort() {
        return sort;
    }
}
