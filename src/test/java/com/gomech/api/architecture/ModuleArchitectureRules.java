package com.gomech.api.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Single definition of the module boundary rules from
 * ADR-001 (Modular Monolith) and ADR-002 (Module Layering and Dependency Rules).
 *
 * <p>The rules are defined once and applied twice: {@link ModuleArchitectureRulesTest}
 * checks them against production code, and {@link ModuleArchitectureRuleFixturesTest}
 * checks them against deliberately violating fixtures so a rule that has stopped
 * detecting anything cannot pass silently.
 *
 * <p>Every rule carries a {@code because(...)} clause that states the remedy, because an
 * architecture failure is only useful if it tells the author what to do instead.
 */
final class ModuleArchitectureRules {

    /**
     * The only package segments a module exposes to other modules. Everything else
     * behind {@code com.gomech.api.modules.<module>} is module-internal.
     */
    private static final Set<String> PUBLIC_SEGMENTS = Set.of("api", "events");

    /**
     * The four layers every business module is built from, plus the published {@code events} package
     * ADR-003 defines. Nothing else may sit directly under a module.
     */
    private static final Set<String> LAYER_SEGMENTS =
        Set.of("api", "application", "domain", "infrastructure", "events");

    /**
     * Module-internal segments that hold persistence ownership. Reaching into one of
     * these from another module couples two database schemas together.
     */
    private static final Set<String> PERSISTENCE_SEGMENTS =
        Set.of("repositories", "models", "entities", "persistence", "infrastructure");

    private static final String MODULES_SEGMENT = "modules";

    /*
     * Layer packages are listed explicitly per root instead of as a bare "..api.." pattern, because
     * the application's own root package is com.gomech.api: "..api.." would match every class in
     * the codebase. Core is included because its audit/authorization/entitlement slices deliberately
     * use the same four-layer layout as a business module, and were previously unprotected.
     */
    private static final String[] API_PACKAGES = {"..modules..api..", "..core..api.."};
    private static final String[] APPLICATION_PACKAGES = {"..modules..application..", "..core..application.."};
    private static final String[] DOMAIN_PACKAGES = {"..modules..domain..", "..core..domain.."};
    private static final String[] INFRASTRUCTURE_PACKAGES = {"..modules..infrastructure..", "..core..infrastructure.."};
    private static final String[] OUTER_LAYER_PACKAGES = {
        "..modules..api..", "..core..api..",
        "..modules..application..", "..core..application..",
        "..modules..infrastructure..", "..core..infrastructure.."
    };

    /**
     * An HTTP adapter. Detected by annotation and by name so the rules keep working for a controller
     * that has not been annotated yet, and so test fixtures can express the violation without
     * carrying a Spring stereotype that component scanning would pick up.
     */
    private static final DescribedPredicate<JavaClass> HTTP_CONTROLLERS =
        JavaClass.Predicates.simpleNameEndingWith("Controller")
            .or(annotatedWith("org.springframework.web.bind.annotation.RestController"))
            .or(annotatedWith("org.springframework.stereotype.Controller"))
            .as("an HTTP controller");

    private ModuleArchitectureRules() {
    }

    // ------------------------------------------------------------ module layout

    static ArchRule modulesMustFollowTheFourLayerLayout() {
        DescribedPredicate<JavaClass> insideABusinessModule =
            new DescribedPredicate<>("reside in a business module") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return moduleOf(javaClass) != null;
                }
            };

        return classes()
            .that(insideABusinessModule)
            .should(new ArchCondition<>("live in one of the four module layers") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String segment = segmentAfterModule(item);
                    if (segment != null && LAYER_SEGMENTS.contains(segment)) {
                        return;
                    }
                    events.add(SimpleConditionEvent.violated(item, String.format(
                        "%s sits in '%s', which is not one of the module layers %s. Fix: classify it by "
                            + "responsibility and move it, for example controllers and DTOs to api, use "
                            + "cases to application, business concepts to domain, JPA and framework "
                            + "adapters to infrastructure.",
                        item.getName(),
                        segment == null ? "the module root" : segment,
                        LAYER_SEGMENTS)));
                }
            })
            .as("modules_must_follow_the_four_layer_layout")
            .because("ADR-002: every business module uses the same internal layering, so the layer rules "
                + "below apply to every module without needing to be updated per module.");
    }

    /*
     * The three rules below classify by responsibility rather than by package name, so a class ends
     * up in the wrong layer even if the package it was dropped into is spelled correctly. They carry
     * a `that()` clause with no allowEmptyShould, which makes them self-guarding: if the codebase
     * ever contains no controller, no entity, or no repository, the rule fails instead of passing
     * vacuously.
     */

    static ArchRule controllersMustResideInTheApiLayer() {
        return classes()
            .that(HTTP_CONTROLLERS)
            .should().resideInAnyPackage(API_PACKAGES)
            .as("controllers_must_reside_in_the_api_layer")
            .because("ADR-002: the HTTP adapter belongs to the module's api layer. Fix: move the controller "
                + "to <module>.api and keep the use case it calls in application.");
    }

    static ArchRule jpaEntitiesMustResideInTheInfrastructureLayer() {
        return classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAnyPackage(INFRASTRUCTURE_PACKAGES)
            .as("jpa_entities_must_reside_in_the_infrastructure_layer")
            .because("ADR-002: JPA entities are a persistence detail owned by infrastructure. Fix: move the "
                + "entity to <module>.infrastructure.persistence and expose a DTO or a domain type instead.");
    }

    static ArchRule springDataRepositoriesMustResideInTheInfrastructureLayer() {
        return classes()
            .that(JavaClass.Predicates.simpleNameEndingWith("Repository")
                .or(assignableTo("org.springframework.data.repository.Repository"))
                .as("a repository"))
            .should().resideInAnyPackage(INFRASTRUCTURE_PACKAGES)
            .as("spring_data_repositories_must_reside_in_the_infrastructure_layer")
            .because("ADR-002: Spring Data repositories are persistence adapters owned by infrastructure. "
                + "Fix: move the repository to <module>.infrastructure.persistence and let application "
                + "call it from there.");
    }

    // ---------------------------------------------------------------- layering

    static ArchRule domainMustNotDependOnOuterLayers() {
        return noClasses()
            .that().resideInAnyPackage(DOMAIN_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAnyPackage(OUTER_LAYER_PACKAGES)
            .as("domain_must_not_depend_on_outer_layers")
            .because("ADR-002: domain is the innermost layer and must stay independent of transport, "
                + "orchestration, and persistence. Fix: define a port interface in domain and implement "
                + "it in infrastructure, or move the orchestration into application.")
            ;
    }

    static ArchRule domainMustNotDependOnFrameworks() {
        return noClasses()
            .that().resideInAnyPackage(DOMAIN_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..")
            .as("domain_must_not_depend_on_frameworks")
            .because("ADR-002: business rules must be testable without Spring, JPA, or a servlet container. "
                + "Fix: keep the framework annotation on the infrastructure adapter or the JPA entity, "
                + "not on the aggregate or value object.")
            ;
    }

    /**
     * ADR-002 places request/response DTOs in {@code api} and does not list {@code application -> api}
     * among its disallowed dependencies; its enforcement table forbids depending on api
     * <em>implementations</em>. So the boundary that matters is the HTTP adapter: a use case may be
     * handed an api DTO, but must never reach for a controller or a Spring web type.
     */
    static ArchRule applicationMustNotDependOnApiControllers() {
        DescribedPredicate<JavaClass> webAdapter = resideInAPackage("org.springframework.web..")
            .or(annotatedWith("org.springframework.web.bind.annotation.RestController"))
            .or(annotatedWith("org.springframework.stereotype.Controller"))
            .as("an api controller or a Spring web type");

        return noClasses()
            .that().resideInAnyPackage(APPLICATION_PACKAGES)
            .should().dependOnClassesThat(webAdapter)
            .as("application_must_not_depend_on_api_controllers")
            .because("ADR-002: use cases must not depend on their own HTTP layer. Fix: keep the controller "
                + "calling the application service, never the reverse, and keep servlet and Spring MVC "
                + "types out of the use case. Request/response DTOs live in api by design and are allowed.");
    }

    static ArchRule apiMustNotAccessInfrastructureDirectly() {
        return noClasses()
            .that().resideInAnyPackage(API_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAnyPackage(INFRASTRUCTURE_PACKAGES)
            .as("api_must_not_access_infrastructure_directly")
            .because("ADR-002: controllers must go through application services, never straight to a "
                + "repository or external client. Fix: call an application service and return a DTO.")
            ;
    }

    // ----------------------------------------------------------- core direction

    static ArchRule coreMustNotDependOnBusinessModules() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAPackage("..modules..")
            .as("core_must_not_depend_on_business_modules")
            .because("ADR-002: core is shared, cross-cutting scaffolding that every module may use, so it "
                + "must never point back at a business module. Fix: move the class into the module that "
                + "owns the behaviour, or define an interface in core that the module implements.")
            ;
    }

    // --------------------------------------------------- cross-module contracts

    static ArchRule crossModuleAccessMustNotTargetPersistence() {
        return classes()
            .should(notDependOn(
                "persistence owned by another module",
                ModuleArchitectureRules::isPersistenceTypeOfAnotherModule,
                "owns that table. Fix: expose an application contract in the owning module's api package, "
                    + "or publish an event the consuming module reacts to. Never read another module's "
                    + "repositories, JPA entities, or persistence adapters."))
            .as("cross_module_access_must_not_target_persistence")
            .because("ADR-002: persistence ownership is module-local.");
    }

    static ArchRule crossModuleAccessMustTargetPublicContractsOnly() {
        return classes()
            .should(notDependOn(
                "a non-public package of another module",
                ModuleArchitectureRules::isInternalTypeOfAnotherModule,
                "exposes only its 'api' and 'events' packages. Fix: import the published contract from "
                    + "that module's api package, or consume one of its events."))
            .as("cross_module_access_must_target_public_contracts_only")
            .because("ADR-002: modules communicate through explicit contracts and events only.");
    }

    static ArchRule repositoriesMustNotBeImportedOutsideTheirModule() {
        return classes()
            .should(notDependOn(
                "a repository owned by another module",
                (origin, target) -> isRepository(target) && isForeignModule(origin, target),
                "owns that repository. Fix: ask the owning module for the data through its api contract "
                    + "instead of importing its repository interface."))
            .as("repositories_must_not_be_imported_outside_their_module")
            .because("ADR-002: no module may read or write another module's repositories directly.");
    }

    /**
     * A module's {@code api} package is public, but not everything in it is a contract: the
     * controllers in there are HTTP adapters for the outside world, not an entry point for a sibling
     * module. Without this rule the public-surface rule would happily allow one module to call
     * another module's controller, because it only looks at the package segment.
     */
    static ArchRule modulesMustNotDependOnAnotherModulesControllers() {
        return classes()
            .should(notDependOn(
                "a controller owned by another module",
                (origin, target) -> HTTP_CONTROLLERS.test(target) && isForeignModule(origin, target),
                "publishes contracts, not HTTP adapters. Fix: call the application contract the owning "
                    + "module exposes in its api package, or react to one of its events. A controller is "
                    + "an entry point for HTTP clients only."))
            .as("modules_must_not_depend_on_another_modules_controllers")
            .because("ADR-002: cross-module communication uses explicit contracts and events only.");
    }

    /**
     * Modules may use everything core publishes as an abstraction, but must bind to the contract, not
     * to the implementation that happens to satisfy it today, several of which are placeholders.
     */
    static ArchRule modulesMustNotDependOnCoreInfrastructure() {
        return noClasses()
            .that().resideInAPackage("..modules..")
            .should().dependOnClassesThat()
            .resideInAPackage("..core..infrastructure..")
            .as("modules_must_not_depend_on_core_infrastructure")
            .because("ADR-002: core exposes abstractions for modules to depend on. Fix: inject the interface "
                + "from the core slice's application package and let Spring supply the implementation.");
    }

    /**
     * ADR-008 separates the event <em>contracts</em> ({@code DomainEvent}, {@code EventEnvelope},
     * {@code DomainEventBus}, {@code DomainEventHandler}) from the Spring machinery that implements
     * them. Modules publish and consume through the contracts; the bus, dispatcher, registry and
     * metadata factory are wiring they must never bind to.
     *
     * <p>These live in the flat {@code core.events} package rather than an {@code infrastructure}
     * one, exactly as ADR-008 names them, so the generic core-infrastructure rule does not cover
     * them and they are listed explicitly here.
     */
    static ArchRule modulesMustUseTheEventBusContractNotItsImplementation() {
        Set<String> implementations = Set.of(
            "com.gomech.api.core.events.SpringDomainEventBus",
            "com.gomech.api.core.events.SpringDomainEventDispatcher",
            "com.gomech.api.core.events.EventHandlerRegistry",
            "com.gomech.api.core.events.EventMetadataFactory"
        );

        return noClasses()
            .that().resideInAPackage("..modules..")
            .should().dependOnClassesThat(new DescribedPredicate<>("an event bus implementation") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return implementations.contains(javaClass.getName());
                }
            })
            .as("modules_must_use_the_event_bus_contract_not_its_implementation")
            .because("ADR-008: publishers depend on the DomainEventBus contract and consumers on "
                + "DomainEventHandler. Fix: inject DomainEventBus instead of SpringDomainEventBus, and "
                + "register a DomainEventHandler bean instead of touching the registry or dispatcher.");
    }

    static ArchRule moduleContractsMustLiveInApiOrEventsPackages() {
        return classes()
            .that().haveSimpleNameEndingWith("Event")
            .or().haveSimpleNameEndingWith("Client")
            .or().haveSimpleNameEndingWith("UseCase")
            .should().resideInAnyPackage(
                "..modules..api..", "..modules..events..", "..modules..application..", "..core.events..")
            .as("module_contracts_must_live_in_api_or_events_packages")
            .because("ADR-002 and ADR-003: a type other modules are meant to consume must live in a package "
                + "that is published on purpose. Fix: move it to the module's api or events package, or to "
                + "core.events if it belongs to the shared event kernel.")
            ;
    }

    static ArchRule modulesMustBeFreeOfCycles() {
        return slices()
            .matching("..modules.(*)..")
            .should().beFreeOfCycles()
            .as("modules_must_be_free_of_cycles")
            .because("ADR-001: a cycle between modules means neither can be understood, tested, or extracted "
                + "on its own. Fix: invert one direction with an event, or move the shared concept into the "
                + "module that owns it.")
            .allowEmptyShould(true);
    }

    // ------------------------------------------------------------ transactions

    static ArchRule controllersMustNotBeTransactional() {
        return noClasses()
            .that(HTTP_CONTROLLERS)
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .as("controllers_must_not_be_transactional")
            .because("ADR-012: transaction boundaries belong to the application layer, not HTTP controllers. "
                + "Fix: remove @Transactional from the controller and manage the transaction inside the application service.");
    }

    static ArchRule controllerMethodsMustNotBeTransactional() {
        return noMethods()
            .that().areDeclaredInClassesThat(HTTP_CONTROLLERS)
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .as("controller_methods_must_not_be_transactional")
            .because("ADR-012: transaction boundaries belong to the application layer, not HTTP controllers. "
                + "Fix: remove @Transactional from the controller method and manage the transaction inside the application service.");
    }

    static ArchRule repositoriesMustNotDeclareTransactional() {
        return noClasses()
            .that(JavaClass.Predicates.simpleNameEndingWith("Repository")
                .or(assignableTo("org.springframework.data.repository.Repository")))
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .as("repositories_must_not_declare_transactional")
            .because("ADR-012: transaction boundaries belong to application use cases. Fix: remove @Transactional "
                + "from the repository interface and manage transactions in the application service.");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a condition that reports every direct dependency matching {@code forbidden}.
     * The failure message names the origin, the target, and the way out.
     */
    private static ArchCondition<JavaClass> notDependOn(
        String description,
        ForbiddenDependency forbidden,
        String remedy
    ) {
        return new ArchCondition<>("not depend on " + description) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    if (!forbidden.matches(origin, target)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(origin, String.format(
                        "%s depends on %s, but module '%s' %s (%s)",
                        origin.getName(),
                        target.getName(),
                        moduleOf(target),
                        remedy,
                        dependency.getDescription()
                    )));
                }
            }
        };
    }

    @FunctionalInterface
    private interface ForbiddenDependency {
        boolean matches(JavaClass origin, JavaClass target);
    }

    private static boolean isPersistenceTypeOfAnotherModule(JavaClass origin, JavaClass target) {
        if (!isForeignModule(origin, target)) {
            return false;
        }
        if (isRepository(target)) {
            return true;
        }
        String segment = segmentAfterModule(target);
        return segment != null && PERSISTENCE_SEGMENTS.contains(segment);
    }

    private static boolean isInternalTypeOfAnotherModule(JavaClass origin, JavaClass target) {
        if (!isForeignModule(origin, target)) {
            return false;
        }
        String segment = segmentAfterModule(target);
        return segment == null || !PUBLIC_SEGMENTS.contains(segment);
    }

    private static boolean isRepository(JavaClass target) {
        return target.getSimpleName().endsWith("Repository") && moduleOf(target) != null;
    }

    /** True when target belongs to a module and origin does not belong to that same module. */
    private static boolean isForeignModule(JavaClass origin, JavaClass target) {
        String targetModule = moduleOf(target);
        return targetModule != null && !targetModule.equals(moduleOf(origin));
    }

    /** The module a class belongs to, or {@code null} when it sits outside any module. */
    private static String moduleOf(JavaClass javaClass) {
        String[] segments = javaClass.getPackageName().split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            if (MODULES_SEGMENT.equals(segments[i])) {
                return segments[i + 1];
            }
        }
        return null;
    }

    /** The first package segment after the module name, for example "api" or "repositories". */
    private static String segmentAfterModule(JavaClass javaClass) {
        String[] segments = javaClass.getPackageName().split("\\.");
        for (int i = 0; i < segments.length - 2; i++) {
            if (MODULES_SEGMENT.equals(segments[i])) {
                return segments[i + 2];
            }
        }
        return null;
    }
}
