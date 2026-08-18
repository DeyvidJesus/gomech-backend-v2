package com.gomech.api.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that every rule in {@link ModuleArchitectureRules} still detects the violation it was
 * written for, by checking it against the deliberately violating fixtures in
 * {@code com.gomech.api.architecture.fixtures}.
 *
 * <p>Without this test a rule could stop matching anything, for example after a package rename or
 * a typo in a package pattern, and keep passing against production code forever.
 *
 * <p>Each assertion also checks that the failure message names the offending class and states the
 * remedy, so an architecture failure tells the author what to do instead of only what not to do.
 */
class ModuleArchitectureRuleFixturesTest {

    private final JavaClasses fixtureClasses = new ClassFileImporter()
        .importPackages("com.gomech.api.architecture.fixtures", "com.gomech.archfixtures");

    @Test
    void domain_reaching_into_infrastructure_is_detected() {
        assertDetects(
            ModuleArchitectureRules.domainMustNotDependOnOuterLayers(),
            "DomainReachingIntoInfrastructure",
            "define a port interface in domain");
    }

    @Test
    void domain_reaching_into_a_framework_is_detected() {
        assertDetects(
            ModuleArchitectureRules.domainMustNotDependOnFrameworks(),
            "DomainReachingIntoFramework",
            "testable without Spring");
    }

    @Test
    void application_reaching_into_the_web_layer_is_detected() {
        assertDetects(
            ModuleArchitectureRules.applicationMustNotDependOnApiControllers(),
            "ApplicationReachingIntoWebLayer",
            "keep the controller calling the application service");
    }

    @Test
    void controller_outside_the_api_layer_is_detected() {
        assertDetects(
            ModuleArchitectureRules.controllersMustResideInTheApiLayer(),
            "MisplacedController",
            "move the controller to <module>.api");
    }

    @Test
    void jpa_entity_outside_the_infrastructure_layer_is_detected() {
        assertDetects(
            ModuleArchitectureRules.jpaEntitiesMustResideInTheInfrastructureLayer(),
            "MisplacedJpaEntity",
            "move the entity to <module>.infrastructure.persistence");
    }

    @Test
    void repository_outside_the_infrastructure_layer_is_detected() {
        assertDetects(
            ModuleArchitectureRules.springDataRepositoriesMustResideInTheInfrastructureLayer(),
            "CrmCustomerRepository",
            "move the repository to <module>.infrastructure.persistence");
    }

    @Test
    void depending_on_another_modules_controller_is_detected() {
        assertDetects(
            ModuleArchitectureRules.modulesMustNotDependOnAnotherModulesControllers(),
            "InventoryReachingIntoCrmController",
            "publishes contracts, not HTTP adapters");
    }

    @Test
    void module_reaching_into_core_infrastructure_is_detected() {
        assertDetects(
            ModuleArchitectureRules.modulesMustNotDependOnCoreInfrastructure(),
            "InventoryReachingIntoCoreInfrastructure",
            "inject the interface");
    }

    @Test
    void package_outside_the_four_layers_is_detected() {
        assertDetects(
            ModuleArchitectureRules.modulesMustFollowTheFourLayerLayout(),
            "CrmCustomerRepository",
            "classify it by responsibility and move it");
    }

    @Test
    void api_reaching_into_infrastructure_is_detected() {
        assertDetects(
            ModuleArchitectureRules.apiMustNotAccessInfrastructureDirectly(),
            "ApiReachingIntoInfrastructure",
            "call an application service");
    }

    @Test
    void core_reaching_into_a_business_module_is_detected() {
        assertDetects(
            ModuleArchitectureRules.coreMustNotDependOnBusinessModules(),
            "CoreReachingIntoModule",
            "move the class into the module that owns the behaviour");
    }

    @Test
    void cross_module_persistence_access_is_detected() {
        assertDetects(
            ModuleArchitectureRules.crossModuleAccessMustNotTargetPersistence(),
            "InventoryReachingIntoCrmRepository",
            "expose an application contract");
    }

    @Test
    void cross_module_access_to_a_non_public_package_is_detected() {
        assertDetects(
            ModuleArchitectureRules.crossModuleAccessMustTargetPublicContractsOnly(),
            "InventoryReachingIntoCrmInternals",
            "exposes only its 'api' and 'events' packages");
    }

    @Test
    void foreign_repository_import_is_detected() {
        assertDetects(
            ModuleArchitectureRules.repositoriesMustNotBeImportedOutsideTheirModule(),
            "InventoryReachingIntoCrmRepository",
            "ask the owning module for the data");
    }

    @Test
    void module_binding_to_the_event_bus_implementation_is_detected() {
        assertDetects(
            ModuleArchitectureRules.modulesMustUseTheEventBusContractNotItsImplementation(),
            "InventoryReachingIntoEventBusImplementation",
            "inject DomainEventBus instead of SpringDomainEventBus");
    }

    @Test
    void contract_hidden_outside_api_or_events_is_detected() {
        assertDetects(
            ModuleArchitectureRules.moduleContractsMustLiveInApiOrEventsPackages(),
            "MisplacedContractEvent",
            "move it to the module's api or events package");
    }

    @Test
    void cycle_between_modules_is_detected() {
        assertDetects(
            ModuleArchitectureRules.modulesMustBeFreeOfCycles(),
            "CrmContract",
            "invert one direction with an event");
    }

    /**
     * A rule passes this check only when it fails against the fixtures, names the offending class,
     * and explains the remedy.
     */
    private void assertDetects(ArchRule rule, String expectedOffender, String expectedRemedy) {
        AssertionError failure = assertThrows(
            AssertionError.class,
            () -> rule.check(fixtureClasses),
            () -> "Rule no longer detects its violation fixture: " + rule.getDescription());

        String message = failure.getMessage();
        assertNotNull(message, "architecture failure must carry a message");
        assertTrue(
            message.contains(expectedOffender),
            () -> "failure must name the offending class '" + expectedOffender + "' but was: " + message);
        assertTrue(
            message.contains(expectedRemedy),
            () -> "failure must state the remedy '" + expectedRemedy + "' but was: " + message);
    }
}
